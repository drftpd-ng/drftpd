#!/bin/sh
source env.sh
export VMARGS="-Djava.library.path=. -Djava.rmi.server.hostname=213.114.146.34 -Djava.rmi.server.randomIDs=true -Djava.rmi.server.disableHttp=true"
exec java ${VMARGS} "$@" net.sf.drftpd.master.ConnectionManager
