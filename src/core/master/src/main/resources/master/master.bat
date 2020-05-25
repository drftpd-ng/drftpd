set CLASSPATH="lib/*;build/*"
rem Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master
set JVM_OPTS="-Xmx3G -XX:+UseG1GC"
set OPTIONS="-Dlog4j.configurationFile=config/log4j2-master.xml"
set PROGRAM="org.drftpd.master.Master"

java %JVM_OPTS% -classpath %CLASSPATH% %OPTIONS% %PROGRAM%