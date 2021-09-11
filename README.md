<h1 align="center">
  <a href="http://drftpd.org/"><img src="https://github.com/drftpd-ng/drftpd-ng.github.io/blob/2611e3c1010b6c3c9185a542fb6075eb3c697128/docs/resources/logo.jpg" alt="DrFTPD"></a>
</h1>
<p align="center">
  <a href="https://circleci.com/gh/drftpd-ng" alt="Build"><img src="https://circleci.com/gh/drftpd-ng/drftpd/tree/master.svg?style=shield" /></a>
  <a href="https://github.com/drftpd-ng/drftpd/wiki/Documentation" alt="Documentation"><img src="https://img.shields.io/badge/Documentation-RTFM-orange.svg" /></a>
</p>

## About DrFTPD
DrFTPD is a Distributed FTP server written in Java, it's unique because it doesn't handle transfers like normal FTP servers.
DrFTPD is set up with a master and a collection of file transfer slaves that handle the file transfers, you can have as many file transfer slaves as you like.
Some names that could be used to describe this is ftp site merger, ftp cluster, ftp grid or multi site bnc, but the only accurate term is "distributed ftp daemon."

What is unique with DrFTPD is that it works with existing FTP client software, you can use the FTP client application you're used to and make site-to-site (FXP) transfers with normal FTP servers.
The only exception to DrFTPD is with passive (PASV) mode. For this the client needs to support the PRET command. PRET is already supported in several of the most widely used FTP clients.
You can often do without PASV mode unless you are behind a firewall which you don't have access to or you need to FXP with another DrFTPD server or a server which doesn't support PASV.

If you merge 10*1gbit sites, you don't get a 10gbit site but you get a 10x1gbit site. What this means is that the aggregate bandwidth is 10gbit but a single transfer will never go above 1gbit.

DrFTPD's approach to the file system and file transfers is what makes it unique. Each file can, and will, end up on a different transfer slave.

DrFTPD uses transfer slaves for all file storage and transfers, it supports but doesn't require a file transfer slave to be run locally.
The master therefore uses very little bandwidth. FTP control connection, data connections for file listings and instructions to the slaves, are the only operations that consume bandwidth on the master.

The master has a filelist that keeps track of which slaves have which files and information about those files. A file can exist on multiple slaves for redundancy and more bandwidth.

When a slave is started, it gathers a filelist and sends the entire list to the master.
The master merges this list with it's existing file list and makes sure that it's in-sync with it's existing file list by adding and removing files to it's own list.
Because the master doesn't have any files locally, modifications to the virtual filesystem cannot be done easily from outside of the DrFTPD application.

Neither the master or the slaves need root privileges. The virtual filesystem contained on the master of which slaves files reside on is the authoritative source for information about the files.
Items like lastModified, size, user, and group, are all kept on the master.
The slave does however require exclusive write access to the storage area, otherwise it will become unsynced with the master filelist and errors can occur.

The slave is kept thin/dumb and isn't told anything about users. It simply processes the instructions that are given to the master and knows nothing about the ftp protocol.
This is an advantage as it simplifies administration of the slaves.

## How to get started

### Requirements

DrFTPD 4.x installation requires a number of steps before you can utilize the software to its full extend.
To give an overview of the installation process the different steps are listed below in this section.

On the master you will need to:
- Install Java JDK or OpenJDK 15, Apache Maven, Git

On the slaves you will need to:
- Install Java SE or OpenJDK 15.
- Add needed deps that are not present:
  - MediaInfo (CLI): https://mediaarea.net/en/MediaInfo
  - mkvalidator tool: https://github.com/Matroska-Org/foundation-source

## Stable version
### Git (better way) or Release

Clone the project from https://github.com/drftpd-ng/drftpd.git or Download release from https://github.com/drftpd-ng/drftpd/releases

Git:
- Run `git clone https://github.com/drftpd-ng/drftpd.git`
- Run `cd drftpd`
- Run `git checkout tags/4.0.4`

Release:
- Run `wget https://github.com/drftpd-ng/drftpd/archive/4.0.4.tar.gz`
- Run `tar xvzf 4.0.4.tar.gz`
- Run `cd drftpd-4.0.4`

### Compile

Maven:
- Optional (Upgrade only), Run `mvn clean` and delete all files from build and lib folders (Master & Slave)
- Run `mvn validate`
- Run `mvn install`

Check generated runtime directory

### Master

- Copy .dist files to .conf only if you change the settings from config and plugins folders (at least `master.conf`)
- Optional: Run `./genkey.sh` for Linux or `genkey.bat` for Windows (if you want to use SSL (Master & Slave))
- Run `./master.sh` for Linux or `master.bat` for Windows
- Connect to `127.0.0.1:2121` with `drftpd:drftpd`
- Execute remote commands: `SITE ADDSLAVE SLAVENAME` and `SITE SLAVE SLAVENAME ADDMASK *@IP.MASK`
- Optional: Create Master Service (`systemd`, `sc.exe` ...)

### Slave

- Copy .dist files to .conf only if you change the settings from config folder (at least `slave.conf`)
- Optional: Copy the `drftpd.key` from the master to the config directory
- Run `./slave.sh` for Linux or `slave.bat` for Windows
- Optional: Create Slave Service (`systemd`, `sc.exe` ...)

## Unstable version
### Git

Clone the project from https://github.com/drftpd-ng/drftpd.git

Git:
- Run `git clone https://github.com/drftpd-ng/drftpd.git`
- Run `cd drftpd`

### Compile

Maven:
- Optional (Upgrade only), Run `mvn clean` and delete all files from build and lib folders (Master & Slave)
- Run `mvn validate`
- Run `mvn install`

Check generated runtime directory

### Master

- Copy .dist files to .conf only if you change the settings from config and plugins folders (at least `master.conf`)
- Optional: Run `./genkey.sh` for Linux or `genkey.bat` for Windows (if you want to use SSL (Master & Slave))
- Run `./master.sh` for Linux or `master.bat` for Windows
- Connect to `127.0.0.1:2121` with `drftpd:drftpd`
- Optional: Create Master Service (`systemd`, `sc.exe` ...)

### Slave

- Copy .dist files to .conf only if you change the settings from config folder (at least `slave.conf`)
- Optional: Copy the `drftpd.key` from the master to the config directory
- Run `./slave.sh` for Linux or `slave.bat` for Windows
- Optional: Create Slave Service (`systemd`, `sc.exe` ...)

### For dev (debug only)
## Git

Clone the project from https://github.com/drftpd-ng/drftpd.git

Git:
- Run `git clone https://github.com/drftpd-ng/drftpd.git`
- Run `cd drftpd`
- Open `pom.xml` from .dev folder with IntelliJ IDEA Community
- IntelliJ:
Run `mvn package`

#### Master 
Create new Application via Run -> Edit Configurations

- Name: `Master`
- JDK: `java 15 SDK of 'drftpd-dev' module`
- Main class: `org.drftpd.master.Master`
- eg. Working Directory: `C:\Users\Administrator\Documents\GitHub\drftpd\runtime\master`
- eg. Environment variables: `DRFTPD_CONFIG_PATH=C:\Users\Administrator\Documents\GitHub\drftpd\runtime\master`
- Use `org.drftpd.master.Master`

Start debug Master

#### Slave 
Create new Application via Run -> Edit Configurations

- Name: `Slave`
- JDK: `java 15 SDK of 'drftpd-dev' module`
- Main class: `org.drftpd.slave.Slave`
- eg. Working Directory: `C:\Users\Administrator\Documents\GitHub\drftpd\runtime\slave`
- eg. Environment variables: `DRFTPD_CONFIG_PATH=C:\Users\Administrator\Documents\GitHub\drftpd\runtime\slave`

Start debug Slave

## Documentation (incomplete)
You can find the documentation online at: https://github.com/drftpd-ng/drftpd/wiki

## Support & Bug tracker
- ircs://irc.efnet.org:6697/drftpd - IRC support
- ircs://irc.efnet.org:6697/drftpd-devel - IRC support (too ;-))
- https://github.com/drftpd-ng/drftpd/issues - Bug tracker
