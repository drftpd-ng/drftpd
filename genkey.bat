keytool -genkey -alias drftpd -dname CN=drftpd -keypass drftpd -keystore drftpd.key -storepass drftpd
copy /y drftpd.key %CD%\src\slave\resources\ 
