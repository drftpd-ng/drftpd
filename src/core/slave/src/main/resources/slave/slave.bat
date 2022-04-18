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

rem #######################################################################################################################################################################
rem #
rem # DrFTPD service example:
rem #
rem # https://commons.apache.org/proper/commons-daemon/procrun.html
rem #
rem # or
rem #
rem # TO DO
rem #
rem #######################################################################################################################################################################

set CLASSPATH=lib/*;build/*
rem Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your slave.
set JVM_OPTS=-Xms1G -Xmx1G -XX:+UseZGC
set OPTIONS=-Dlog4j.configurationFile=config/log4j2-slave.xml
set PROGRAM=org.drftpd.slave.Slave

java %JVM_OPTS% -classpath %CLASSPATH% %OPTIONS% %PROGRAM%
