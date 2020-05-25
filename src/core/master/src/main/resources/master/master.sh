#! /bin/sh

CLASSPATH="lib/*;build/*"
# Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master
JVM_OPTS="-Xmx3G -XX:+UseG1GC"
OPTIONS="-Dlog4j.configurationFile=config/log4j2-master.xml"
PROGRAM="org.drftpd.master.Master"

java ${JVM_OPTS} -classpath ${CLASSPATH} ${JVM_OPTS} ${OPTIONS} ${PROGRAM}