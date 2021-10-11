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

#####################################################
#
# DrFTPD service example:
#
# Put the below unit into: ~/.config/systemd/user/drftpd-master.service
#
#
# [Unit]
# Description=DrFTPD Master
# After=network.target
# StartLimitIntervalSec=500
# StartLimitBurst=5
#
# [Service]
# Restart=on-failure
# RestartSec=5s
# Type=simple
# UMask=007
# WorkingDirectory=/home/mastersitename/master
# ExecStart=/home/mastersitename/master/master.sh
#
# [Install]
# WantedBy=multi-user.target
#
#
####################################################
# systemctl daemon-reload --user
# systemctl enable --user drftpd-master.service
# To start the master: systemctl start --user drftpd-master.service
# To stop the master: systemctl stop --user drftpd-master.service
#####################################################

CLASSPATH="lib/*:build/*"
# Add JVM Options here however you see fit and please check if the max memory Xmx is good enough for your master.
JVM_OPTS="-Xmx3G -XX:+UnlockExperimentalVMOptions -XX:+UseZGC"
OPTIONS="-Dlog4j.configurationFile=config/log4j2-master.xml"
PROGRAM="org.drftpd.master.Master"

java ${JVM_OPTS} -classpath ${CLASSPATH} ${OPTIONS} ${PROGRAM}
