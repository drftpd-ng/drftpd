#!/bin/sh
export CLASSPATH="classes:lib/jdom.jar:lib/martyr.jar:lib/oro.jar:lib/JSX1.0.7.4.jar:lib/replacer.jar:lib/log4j-1.2.8.jar"
export VMARGS="
	-Dglftpd.users=/ftp-data/users \
	-Dglftpd.root=/glftpd \
	-Dglftpd.passwd=/etc/users"
exec java ${VMARGS} $@ net.sf.drftpd.master.usermanager.UserManagerConverter net.sf.drftpd.master.usermanager.glftpd.GlftpdUserManager net.sf.drftpd.master.usermanager.jsx.JSXUserManager
