#!/bin/sh
if [ -z "$1" ]
then
 echo "Usage: packageit.sh <version>"
 exit
fi

rm -f drftpd-$1.tar.gz
rm -f drftpd-$1.zip
tar -cf drftpd-$1.tar drftpd-$1
gzip drftpd-$1.tar
zip -r drftpd.zip drftpd-$1
mv drftpd.zip drftpd-$1.zip
