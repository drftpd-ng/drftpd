@echo off
rem $Id$
call "%ANT_HOME%\bin\ant" -buildfile installer.xml
call "%JAVA_HOME%\bin\java" -cp ./lib/*;"%JAVA_HOME%/lib/tools.jar" -Djava.library.path=lib -Dlog4j.configuration=file:log4j-build.properties -Dincludes="src/*/plugin.xml,src/plugins/*/plugin.xml" org.drftpd.tools.installer.Wrapper