#!/bin/sh
source env.sh
echo $CLASSPATH
exec java "$@" net.sf.drftpd.master.ConnectionManager
#exec gij "$@" net.sf.drftpd.master.ConnectionManager
