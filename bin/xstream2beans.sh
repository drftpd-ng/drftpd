#!/bin/sh
ant compile-xstream
export CLASSPATH="classes:lib/log4j-1.2.8.jar:lib/oro.jar:lib/jdom.jar:lib/xstream-1.0.jar:lib/replacer.jar"
exec java $@ org.drftpd.usermanager.UserManagerConverter net.sf.drftpd.master.usermanager.xstream.XStreamUserManager org.drftpd.usermanager.javabeans.BeanUserManager
