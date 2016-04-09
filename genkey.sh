#!/bin/sh
keytool -genkeypair -alias drftpd -keyalg EC -keysize 521 -validity 365 -storetype JKS -keystore drftpd.key -storepass drftpd
#keytool -genkey -alias drftpd -dname CN=drftpd -keypass drftpd -keystore drftpd.key -storepass drftpd -keyalg rsa "$@"
