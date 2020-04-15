<h1 align="center">
  <a href="http://drftpd.org/"><img src="http://drftpd.org/images/Drftpd-logo-4-resize.jpg" alt="DrFTPD"></a>
</h1>
<p align="center">
  <a href="https://circleci.com/gh/drftpd-ng" alt="Build"><img src="https://circleci.com/gh/drftpd-ng/drftpd/tree/v4.svg?style=shield" /></a>
  <a href="http://drftpd.org/" alt="Website"><img src="https://img.shields.io/badge/website-drftpd.org-blue.svg" /></a>
  <a href="https://github.com/drftpd-ng/drftpd3/wiki/Documentation" alt="Documentation"><img src="https://img.shields.io/badge/Documentation-RTFM-orange.svg" /></a>
</p>

# Introduction

Just a test around moving to maven and removing jpf.

Partial stuff, partial support, not for everyone :D

## For dev
Checkout the project
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
mvn install

Check generated runtime directory

master.bat|sh and slave.bat|sh