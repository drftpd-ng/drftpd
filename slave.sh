#!/bin/sh
VMARGS="
	-server
	-Djava.library.path=. \
	-Djava.rmi.dgc.leaseValue=300000 \
	-Djava.rmi.server.randomIDs=true"

export CLASSPATH=classes
exec java $VMARGS net.sf.drftpd.slave.SlaveImpl drftpd.conf
