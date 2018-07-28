set ROOT=%~dp0
set USERDATA_MASTER=%ROOT%userdata\
set USERDATA_SLAVE=%ROOT%src\slave\resources\userdata\
if not exist "%USERDATA_MASTER%" (
  mkdir "%USERDATA_MASTER%"
  if "%errorlevel%" NEQ "0" (
    echo Error while creating userdata folder %USERDATA_MASTER%
    goto :eof
  )
)
if not exist "%USERDATA_SLAVE%" (
  mkdir "%USERDATA_SLAVE%"
  if "%errorlevel%" NEQ "0" (
    echo Error while creating userdata folder %USERDATA_SLAVE%
    goto :eof
  )
)
keytool -genkeypair -keyalg EC -keysize 256 -sigalg SHA256withECDSA -alias drftpd -dname CN=drftpd -keypass drftpd -keystore "%USERDATA_MASTER%drftpd.key" -storetype pkcs12 -storepass drftpd
copy /Y "%USERDATA_MASTER%drftpd.key" "%USERDATA_SLAVE%"