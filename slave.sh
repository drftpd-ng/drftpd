#!/bin/sh
exec java -Djava.library.path=. -Djava.rmi.dgc.leaseValue=300000 -jar slave.jar drftpd.conf
