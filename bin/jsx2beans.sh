#!/bin/sh
ant compile-jsx
export CLASSPATH="classes:lib/log4j-1.2.8.jar:lib/oro.jar:lib/jdom.jar:lib/JSX1.0.7.4.jar:lib/replacer.jar"
exec java $@ org.drftpd.usermanager.UserManagerConverter net.sf.drftpd.master.usermanager.jsx.JSXUserManager org.drftpd.usermanager.javabeans.BeanUserManager
