set VMARGS=-Djava.library.path=. -Djava.rmi.dgc.leaseValue=300000 -Djava.rmi.server.randomIDs=true
set CLASSPATH=log4j-1.2.8.jar:classes
java %VMARGS% net.sf.drftpd.slave.SlaveImpl drftpd.conf
pause

