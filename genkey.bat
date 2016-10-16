set ROOT=%~dp0
set USERDATA=%ROOT%userdata\
if not exist "%USERDATA%" (
  mkdir "%USERDATA%"
  if "%errorlevel%" NEQ "0" (
    echo Error while creating userdata folder %USERDATA%
    goto :eof
  )
)
keytool -genkeypair -keyalg EC -keysize 256 -sigalg SHA256withECDSA -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "%USERDATA%drftpd.key" -storepass drftpd
