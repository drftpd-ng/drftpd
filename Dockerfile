FROM amazoncorretto:25-alpine3.19 AS baseimage
RUN set -ux \
    && adduser drftpd -u 1000 -D -h /home/drftpd


# build mkvalidator from ./foundation-source
FROM baseimage AS foundation
RUN mkdir /foundation-source
COPY foundation-source /foundation-source/
RUN set -ux \
    && apk update \
    && apk add \
        build-base \
        cmake \
    && cd /foundation-source \
    && echo "set(CMAKE_EXE_LINKER_FLAGS "-static")" >> CMakeLists.txt \
    && cmake -S . -B build \
    && cmake --build build


# build drftpd from ./drftpd-source
FROM baseimage AS drftpd-build
RUN mkdir /drftpd-source
COPY drftpd-source /drftpd-source/
RUN set -ux \
    && apk update \
    && apk add \
        git \
        maven \
    && cd /drftpd-source \
    && mvn clean \
    && mvn validate \
    && mvn install \
    && git rev-parse --short HEAD > runtime/master/HEAD.version \
    && git rev-parse --short HEAD > runtime/slave/HEAD.version


# build master image
FROM baseimage AS drftpd-master

USER drftpd
COPY --from=drftpd-build --chown=drftpd:drftpd /drftpd-source/runtime/master/ /home/drftpd/master/
WORKDIR /home/drftpd/master
RUN set -ux \
    && cp -r config config.dist \
    && mkdir index.bkp \
    && mkdir userdata \
    && echo 'cp -rn config.dist/* config/' >> initconfig \
    && echo 'if [ ! -f "config/drftpd.key" ] ; then keytool -genkeypair -keyalg EC -groupname secp384r1 -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "config/drftpd.key" -storetype pkcs12 -storepass drftpd -validity 365 ; fi' >> initconfig \
    && chmod a+x initconfig

# Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master.
ENV JAVA_TOOL_OPTIONS="-Djdk.tls.acknowledgeCloseNotify=true -Xms3G -Xmx3G -XX:+UseG1GC -Dlog4j.configurationFile=config/log4j2-master.xml"
ENTRYPOINT ["java", "-classpath", "lib/*:build/*", "org.drftpd.master.Master"]


# build slave image
FROM baseimage AS drftpd-slave
USER root
COPY --from=foundation --chown=root:root /foundation-source/build/mkvalidator/mkvalidator /usr/local/bin/mkvalidator
RUN set -ux \
    && apk update \
    && apk add \
        mediainfo


USER drftpd
COPY --from=drftpd-build --chown=drftpd:drftpd /drftpd-source/runtime/slave/ /home/drftpd/slave/
WORKDIR /home/drftpd/slave
RUN set -ux \
    && cp -r config config.dist \
    && mkdir site \
    && echo "cp -rn config.dist/* config/" >> initconfig \
    && chmod a+x initconfig

# Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your slave.
ENV JAVA_TOOL_OPTIONS="-Djdk.tls.acknowledgeCloseNotify=true -Xms1G -Xmx1G -XX:+UseG1GC -Dlog4j.configurationFile=config/log4j2-slave.xml"
ENTRYPOINT ["java", "-classpath", "lib/*:build/*", "org.drftpd.slave.Slave"]
