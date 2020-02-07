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

# Installation steps

DrFTPD 3.4.3 installation requires a number of steps before you can utilize the software to its full extend.
To give an overview of the installation process the different steps are listed below in this section.

On the master you will need to:
- Install Oracle Java 1.8 and Apache Ant
- Generate key
- Compile the software using setup wizard
- Configure .conf files

On the slaves you will need to:
- Install Oracle Java 1.8
- Add needed plugins that are not present :
  MediaInfo (CLI): https://mediaarea.net/fr/MediaInfo
  mkvalidator tool: https://www.matroska.org/downloads/mkvalidator.html
- Copy slave.zip to a slave from the master
- Configure slave.conf

## Install Java

Generial info follows:
- Download and install Java Development Kit 8 (JDK) on the master.
- Download and install a Java Runtime Environment 8 (JRE) on the slaves.

You can get Oracle's JDK here: http://www.oracle.com/technetwork/java/javase/downloads/index.html

If you want to utilize blowfish in your environment also download Java Cryptography Extension (JCE) Unlimited Strength Jurisdiction Policy Files 8. You will need to manually replace the files local_policy.jar and US_export_policy.jar in your java/jre/lib/security folder.

### Problems

- Ensure that JAVA_HOME is configured. You can check this using "echo %JAVA_HOME%" on Windows platform or using "echo $JAVA_HOME" on *nix.
- *nix If you encounter problems like "master.sh: line 11: exec: java: not found". You need to add the java binary to your PATH environment variable. Edit your /etc/profile or .bashrc (for current user only) and add PATH=$PATH:$JAVA_HOME/bin at the bottom. Make sure that your enviroment variable $JAVA_HOME is set correctly.
- Windows If you encounter problems like " 'JAVA' is not recognized as an internal or external command, operable program or batch file.". You also need to add the java binary to your PATH environment variable. You can do this in Windows in your System Properties under the Advanced Tab, there is a button Environment Variables, edit your PATH variable accordingly.

These are issues with your Operating System/Java Install and not related to DrFTPD. 

## Install ant

Compiling DrFTPD is required to use the software.
To allow you to compile Java you will need to install Ant or Eclipse.
You can find the installation documentation here: http://ant.apache.org/manual/install.html

### Problems

- Ensure that ANT_HOME is configured. You can check this using "echo %ANT_HOME%" on Windows platform or using "echo $ANT_HOME" on *nix

## Downloading DrFTPD

Download DrFTPD from https://github.com/drftpd-ng/drftpd

### Details

Change to the main DrFTPD folder, for example ~/drftpd (*nix) or c:\drftpd (windows).
```    
For Master Branch:
git clone -b master --single-branch https://github.com/drftpd-ng/drftpd.git 
For Develop Branch:
git clone -b develop --single-branch https://github.com/drftpd-ng/drftpd.git 
cd drftpd
```

## Build DrFTPD

DrFTPD version 3 comes with an installer. Using this installer you are able to choose which components that should be compiled. 

Start the installer 
- *nix
```
./build.sh 
```
- Windows
```
build.bat
```

### Installation instructions

Each plugin should come with its own small installation instruction. Especially if this is not the same installation method as below.
- Upload zip file to src/plugins/
- Make sure that you remove all files leftovers if the plugin already is present. rm -r <plugin_foldername>
- Run Unzip to uncompress files into src/plugins. unzip <plugin_name>.zip
- Run build.sh and choose to activate the plugin, default = no
- Update .conf files according with new conf settings from .dist files.
- Start the site or if already started type SITE LOADPLUGIN <plugin name>

## Slave installation

DrFTPD slaves require Java Runtime Environment 8 (JRE) or Java Development Kit 8 (JDK). Please see above for download information.

Copy the slave.zip file from your master to the server that you plan to run the slave on.
- Download slave.zip from the masters main folder
- Unzip slave.zip
- On *nix you will need to change filemodes
```
chmod 744 slave.sh
chmod 744 bin/wrapper
```
- Copy conf/diskselection.conf.dist to conf/diskselection.conf
- Copy conf/log4j-debug.xml.dist to conf/log4j-debug.xml
- Copy conf/log4j-eclipse.xml.dist to conf/log4j-eclipse.xml
- Copy conf/log4j-slave.xml.dist to conf/log4j-slave.xml
- Copy conf/slave.conf.dist to conf/slave.conf
- Copy conf/wrapper-slave.conf.dist to conf/wrapper-slave.conf
- Copy conf/plugins/speedtest.net.slave.conf.dist to conf/plugins/speedtest.net.slave.conf
- Edit slave.conf

  - The minimum changes that you must complete in slave.conf is to change slave.name, master.host and master.port.
  It is also recommended to specify a range of ports to use for file transfers. Edit slave.portfrom and slave.portto.
  Leave everything else unchanged unless you know what you are doing.

You are now ready to add the slave in the master configuration
```
SITE ADDSLAVE <slavename>
SITE SLAVE <slavename> ADDMASK *@<ipmask_of_slave>
```

You can now start the slave
- *nix
```
./slave.sh
```
- Windows, You would very likely want to add the slave as a service within Windows.
```
bin\InstallSlave.bat
net start drftpd-slave
```

Verify that the slave is coming online with SITE SLAVES.

## Windows Installation

1. Install the JDK suite (just click through all the defaults). Please see above for download information. You do not need the one with netbeans, but just the standard, basic Java JDK.
2. Download Apache-Ant, I take it and unzip it to c:\ant\, such that c:\ant\bin\ant is the ant compiler (this can be tested by going to start->run->cmd and then typing c:\ant\bin\ant,
   ant should fire up and give an error http://ant.apache.org/bindownload.cgi
3. Download and unzip drftpd, i do so to c:\drftpd\.
4. In your System Properties under the Advanced Tab, there is a button entitled "Environment Variables," edit your PATH variable to include the Java directory (i.e. c:\program files\java\jre8\bin),
   also set your JAVA_HOME to this path, but without the bin appeneded to the end (i.e. c:\program files\java\jre\)
5. Open a command prompt (start->run->cmd) and type cd c:\drftpd\, then type c:\ant\bin\ant and it should compile DrFTPD. If any errors are generated, something went wrong, and DrFTPD most likely will not function.

The windows x64 wrapper is no longer included, if you want to integrate it, you must recover the sources from https://wrapper.tanukisoftware.com/doc/english/download.jsp

# FAQ 

- java.lang.Error: failed instanciating SAX parser
You did not setup JAVA and ANT correctly. Check if ANT and JAVA commands execute the right Java version

- org.drftpd.exceptions.SSLUnavailableException: Secure connections to slave required but SSL isn't available
You did not setup JAVA correctly. Check if the KEYTOOL is the one from your used Java version and if you generated and copyed the drftpd.key

- Exception in thread "main" java.lang.UnsatisfiedLinkError: /..../libTerminal.so
You are likely trying to build on a x64 OS. Basically you will need to replace wrapper with a 64bit version, and Java SDK 64bit .... and libTerminal.so has to be compiled for 64bit aswell.
Search on libTerminal.so where you can test a few versions that people have uploaded on the forum (or compile your own).

# Online Help 

If you use IRC, connect to EFnet and join #drftpd, #drftpd-devel. Alternatively use the github repository https://github.com/drftpd-ng/drftpd
