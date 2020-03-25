#! /bin/sh
java -Xms1024M -classpath "build/*" -Dlog4j.configurationFile=config/log4j2-slave.xml org.drftpd.SlaveBoot