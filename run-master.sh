#!/bin/sh
export CLASSPATH=classes:lib/jakarta-oro-2.0.6.jar
exec java net.sf.drftpd.master.Main
