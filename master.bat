set VMARGS=-Djava.library.path=lib -Djava.rmi.server.hostname=you_didnt_edit_master.bat -Djava.rmi.server.randomIDs=true -Djava.rmi.server.disableHttp=true
set CLASSPATH=classes;lib/jdom.jar;lib/martyr.jar;lib/oro.jar;lib/JSX1.0.7.4.jar;lib/replacer.jar;lib/log4j-1.2.8.jar
java %VMARGS% net.sf.drftpd.master.ConnectionManager
pause
