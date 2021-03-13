#!/bin/sh
#
# This file is part of DrFTPD, Distributed FTP Daemon.
#
# DrFTPD is free software; you can redistribute it and/or
# modify it under the terms of the GNU General Public License
# as published by the Free Software Foundation; either version 2
# of the License, or (at your option) any later version.
#
# DrFTPD is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License
# along with DrFTPD; if not, write to the Free Software
# Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
#

################################################################
#
# DrFTPD service example:
#
# nano /etc/systemd/system/slave-drftpd.service
#
# [Unit]
# Description=DrFTPD Slave
# After=network.target
# StartLimitIntervalSec=500
# StartLimitBurst=5
#
# [Service]
# Restart=on-failure
# RestartSec=5s
# Type=simple
# User=drftpd-service
# Group=drftpd-service
# UMask=007
# WorkingDirectory=/home/drftpd-service/drftpd/runtime/slave
# ExecStart=/home/drftpd-service/drftpd/runtime/slave/slave.sh
#
# [Install]
# WantedBy=multi-user.target
#
# systemctl daemon-reload
# systemctl enable --now drftpd-slave.service
#
################################################################

CLASSPATH="lib/*:build/*"
# Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your slave.
JVM_OPTS="-Xmx1G -XX:+UseZGC"
OPTIONS="-Dlog4j.configurationFile=config/log4j2-slave.xml"
PROGRAM="org.drftpd.slave.Slave"

java ${JVM_OPTS} -classpath ${CLASSPATH} ${OPTIONS} ${PROGRAM}