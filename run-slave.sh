#!/bin/sh
#source env.sh
export CLASSPATH=classes
exec java -Djava.library.path=. net.sf.drftpd.slave.SlaveImpl
