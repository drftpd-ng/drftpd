set VMARGS=-Djava.library.path=lib
set VMARGS=%VMARGS% -Djava.rmi.dgc.leaseValue=300000
set VMARGS=%VMARGS% -Dlog4j.configuration=file:log4j-default.properties
set VMARGS=%VMARGS% -Djava.rmi.server.randomIDs=true
set VMARGS=%VMARGS% -Djava.rmi.server.disableHttp=true
#set VMARGS=%VMARGS% -Djava.rmi.server.hostname=you_didnt_edit_master.bat 

set CLASSPATH=classes;lib/jdom.jar;lib/martyr.jar;lib/oro.jar;lib/JSX1.0.7.4.jar;lib/replacer.jar;lib/log4j-1.2.8.jar
java %VMARGS% net.sf.drftpd.master.ConnectionManager
pause
