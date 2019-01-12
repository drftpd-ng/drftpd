#!/bin/sh
# Absolute path to this script
SCRIPT=$(readlink -f "$0")
# Absolute path this script is in
SCRIPTPATH=$(dirname "$SCRIPT")
mkdir -p "$SCRIPTPATH/userdata"
keytool -genkeypair -keyalg EC -keysize 256 -sigalg SHA256withECDSA -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "$SCRIPTPATH/userdata/drftpd.key" -storetype pkcs12 -storepass drftpd "$@"
