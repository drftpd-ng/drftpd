set VMARGS=-Djava.library.path=lib -Djava.rmi.dgc.leaseValue=300000 -Djava.rmi.server.randomIDs=true
set CLASSPATH=classes;lib/log4j-1.2.8.jar
java %VMARGS% net.sf.drftpd.slave.SlaveImpl
pause
