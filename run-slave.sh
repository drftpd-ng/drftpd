#!/bin/sh
source env.sh
exec java -classpath classes net.sf.drftpd.slave.SlaveImpl
