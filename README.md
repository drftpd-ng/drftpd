<h1 align="center">
  <a href="http://drftpd.org/"><img src="http://drftpd.org/images/Drftpd-logo-4-resize.jpg" alt="DrFTPD"></a>
</h1>
<p align="center">
  <a href="https://circleci.com/gh/drftpd-ng" alt="Build"><img src="https://circleci.com/gh/drftpd-ng/drftpd/tree/v4.svg?style=shield" /></a>
  <a href="http://drftpd.org/" alt="Website"><img src="https://img.shields.io/badge/website-drftpd.org-blue.svg" /></a>
  <a href="https://github.com/drftpd-ng/drftpd3/wiki/Documentation" alt="Documentation"><img src="https://img.shields.io/badge/Documentation-RTFM-orange.svg" /></a>
</p>

# Introduction

DrFTPD is a Distributed FTP server written in Java, it's unique because it doesn't handle transfers like normal FTP servers.
DrFTPD is set up with a master and a collection of file transfer slaves that handle the file transfers, you can have as many file transfer slaves as you like.
Some names that could be used to describe this is ftp site merger, ftp cluster, ftp grid or multi site bnc, but the only accurate term is "distributed ftp daemon."

What is unique with DrFTPD is that it works with existing FTP client software, you can use the FTP client application you're used to and make site-to-site (FXP) transfers with normal FTP servers.
The only exception to DrFTPD is with passive (PASV) mode. For this the client needs to support the PRET command. PRET is already supported in several of the most widely used FTP clients.
You can often do without PASV mode unless you are behind a firewall which you don't have access to or you need to FXP with another DrFTPD server or a server which doesn't support PASV.

If you merge 10 100mbit sites, you don't get a 1gbit site but you get a 10x100mbit site. What this means is that the aggregate bandwidth is 1000 mbit but a single transfer will never go above 100mbit.

## Filesystem

DrFTPD's approach to the file system and file transfers is what makes it unique. Each file can, and will, end up on a different transfer slave.

DrFTPD uses transfer slaves for all file storage and transfers, it supports but doesn't require a file transfer slave to be run locally.
The master therefore uses very little bandwidth. FTP control connection, data connections for file listings and instructions to the slaves, are the only operations that consume bandwidth on the master.

The master has a filelist that keeps track of which slaves have which files and information about those files. A file can exist on multiple slaves for redundancy and more bandwidth.

When a slave is started, it gathers a filelist and sends the entire list to the master.
The master merges this list with it's existing file list and makes sure that it's in-sync with it's existing file list by adding and removing files to it's own list.
Because the master doesn't have any files locally, modifications to the virtual filesystem cannot be done easily from outside of the drftpd application.

Neither the master or the slaves need root privileges. The virtual filesystem contained on the master of which slaves files reside on is the authoritative source for information about the files.
Items like lastModified, size, user, and group, are all kept on the master.
The slave does however require exclusive write access to the storage area, otherwise it will become unsynced with the master filelist and errors can occur.

The slave is kept thin/dumb and isn't told anything about users. It simply processes the instructions that are given to the master and knows nothing about the ftp protocol.
This is an advantage as it simplifies administration of the slaves.

## Requirements

DrFTPD 4.0.0-beta1 installation requires a number of steps before you can utilize the software to its full extend.
To give an overview of the installation process the different steps are listed below in this section.

On the master you will need to:
- Install Java JDK or OpenJDK 14 and Apache Maven

On the slaves you will need to:
- Install Java SE or OpenJDK 14 
- Add needed deps that are not present :
  MediaInfo (CLI): https://mediaarea.net/fr/MediaInfo
  mkvalidator tool: https://www.matroska.org/downloads/mkvalidator.html

## For dev

Checkout the project from https://github.com/drftpd-ng/drftpd.git 
Open pom.xml with intellij
Compile and mvn package
Create starter for master and slave

### Master 

Use org.drftpd.master.Master

Start with env var: DRFTPD_CONFIG_PATH=$PROJECT_DIR$/runtime/master

### Slave 

Use org.drftpd.slave.Slave

Start with env var: DRFTPD_CONFIG_PATH=$PROJECT_DIR$/runtime/slave

## For early users

Download xxx

- mvn validate
- mvn install

Check generated runtime directory

### Master

- Generate key
- Copy .dist to .conf files and configure
- master.bat|sh
- Connect to 127.0.0.1:2121 with drftpd:drftpd

### Slave

- Copy key from Master
- Copy .dist to .conf files and configure
- slave.bat|sh

