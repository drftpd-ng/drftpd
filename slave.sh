#!/bin/sh
VMARGS="-server -Djava.library.path=. -Djava.rmi.dgc.leaseValue=300000 -Djava.rmi.server.randomIDs=true"
export CLASSPATH=classes:lib/log4j-1.2.8.jar
exec java ${VMARGS} net.sf.drftpd.slave.SlaveImpl
