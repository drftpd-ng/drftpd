#!/bin/sh
keytool -genkey -alias drftpd -dname CN=drftpd -keypass drftpd -keystore drftpd.key -storepass drftpd "$@"
