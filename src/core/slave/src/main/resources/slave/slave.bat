set CLASSPATH=lib/*;build/*
rem Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master
set JVM_OPTS=-Xmx1G -XX:+UseG1GC
set OPTIONS=-Dlog4j.configurationFile=config/log4j2-slave.xml
set PROGRAM=org.drftpd.slave.Slave

java %JVM_OPTS% -classpath %CLASSPATH% %OPTIONS% %PROGRAM%