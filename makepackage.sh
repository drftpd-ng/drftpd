#!/bin/sh
if [ -z "$1" ] || [ -z "$2" ]
then
 echo "Usage: makepackage.sh <version> <cvsbranch>"
 exit
fi

cvs -d :ext:zubov@cvs.sourceforge.net/cvsroot/drftpd export -r $2 drftpd
cp -R lib extsources drftpd/
rm -rf drftpd/org/drftpd/friendly
cd drftpd
ant compile-jsx
ant slavejar
cd ..
chmod -R 777 drftpd/
chmod 664 drftpd/conf/*.dist
chmod 755 drftpd/bin/*
chmod 664 drftpd/*.txt
chmod 664 drftpd/*.xml
chmod 664 drftpd/*.dist
chmod 755 drftpd/*.sh
chmod 644 drftpd/*.bat
rm -f drftpd/packageit.sh
rm -f drftpd/makepackage.sh
cp wrapper.exe drftpd/
cp wrapper drftpd/bin/
mkdir drftpd/logs/
mv drftpd drftpd-$1
./packageit.sh $1
