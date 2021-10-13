rem #
rem # This file is part of DrFTPD, Distributed FTP Daemon.
rem #
rem # DrFTPD is free software; you can redistribute it and/or
rem # modify it under the terms of the GNU General Public License
rem # as published by the Free Software Foundation; either version 2
rem # of the License, or (at your option) any later version.
rem #
rem # DrFTPD is distributed in the hope that it will be useful,
rem # but WITHOUT ANY WARRANTY; without even the implied warranty of
rem # MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
rem # GNU General Public License for more details.
rem #
rem # You should have received a copy of the GNU General Public License
rem # along with DrFTPD; if not, write to the Free Software
rem # Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
rem #

rem ###########################################################################################################################################################################
rem #
rem # DrFTPD service example:
rem #
rem # sc.exe create drftpd-master binpath=C:\Users\Administrator\Documents\GitHub\drftpd\runtime\master\master.bat type=own start=auto error=normal DisplayName="DrFTPD Master" 
rem #
rem # sc.exe start drftpd-master
rem #
rem ###########################################################################################################################################################################

set CLASSPATH=lib/*;build/*
rem Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master
set JVM_OPTS=-Xmx3G -XX:+UnlockExperimentalVMOptions -XX:+UseZGC
set OPTIONS=-Dlog4j.configurationFile=config/log4j2-master.xml
set PROGRAM=org.drftpd.master.Master

java %JVM_OPTS% -classpath %CLASSPATH% %OPTIONS% %PROGRAM%
