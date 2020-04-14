set ROOT=%~dp0
keytool -genkeypair -keyalg EC -groupname secp384r1 -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "drftpd.key" -storetype pkcs12 -storepass drftpd -validity 365
