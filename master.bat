set VMARGS=-server -Djava.library.path=lib -Djava.rmi.server.hostname=213.114.146.8 -Djava.rmi.server.randomIDs=true -Djava.rmi.server.disableHttp=true
set CLASSPATH=classes;lib/jdom.jar;lib/martyr.jar;lib/oro.jar;lib/JSX1.0.7.4.jar
java %VMARGS% net.sf.drftpd.master.ConnectionManager
pause