#!/bin/sh
source env.sh
exec java "$@" net.sf.drftpd.master.ConnectionManager
