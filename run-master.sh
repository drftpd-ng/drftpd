#!/bin/sh
source env.sh
export VMARGS="-Djava.library.path=. -Djava.rmi.server.hostname=213.114.146.8 -Djava.rmi.server.randomIDs=true -Djava.rmi.server.disableHttp=true -Djava.rmi.server.logCalls=false"
exec java ${VMARGS} "$@" net.sf.drftpd.master.ConnectionManager
