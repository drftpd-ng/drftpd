#!/bin/sh
export CLASSPATH="classes:lib/log4j-1.2.8.jar:lib/xstream-1.0.jar"
exec java $@ org.drftpd.usermanager.UserManagerConverter net.sf.drftpd.master.usermanager.xstream.XStreamUserManager org.drftpd.usermanager.javabeans.BeanUserManager
