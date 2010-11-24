#!/bin/sh
keytool -genkey -alias drftpd -dname CN=drftpd -keypass drftpd -keystore drftpd.key -storepass drftpd -keyalg rsa "$@"
cp ./drftpd.key ./src/slave/resources/
