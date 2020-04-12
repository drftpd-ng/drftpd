set ROOT=%~dp0
set USERDATA=%ROOT%userdata\
if not exist "%USERDATA%" (
  mkdir "%USERDATA%"
  if "%errorlevel%" NEQ "0" (
    echo Error while creating userdata folder %USERDATA%
    goto :eof
  )
)
keytool -genkeypair -keyalg EC -groupname secp384r1 -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "%USERDATA%drftpd.key" -storetype pkcs12 -storepass drftpd -validity 365
