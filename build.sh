#!/bin/sh
# $Id: build.sh 1925 2009-06-15 21:46:05Z tdsoul $

#JAVA_BIN=""
#JAVA_HOME=""
#TOOLS_JAR=""

greadlink() {
	p=$1
	while true; do
		t=$(readlink $p)
		if [ "$?" -ne "0" ]; then
			echo $p
			return 0
		fi
		p=$t
	done
}

abspath() {
	case "$1" in
		[./]*)
			tmp="$(cd ${1%/*}; pwd)/${1##*/}"
		;;
		*)
			tmp="${PWD}/${1}"
		;;
	esac
	echo ${tmp%/}
}

OS=""
ARCH=""
BITS=""
SUFIX=""
case $(uname -s |tr A-Z a-z) in
	linux*)
		OS="linux"
		SUFIX="so"
	;;
	darwin*)
		OS="macosx"
		SUFIX="jnilib"
	;;
	freebsd*)
		OS="freebsd"
		SUFIX="so"
	;;
	*)
		echo "Could not detect os, bailing out"
		exit 1
	;;
esac

case $OS in
	linux|freebsd)
		ARCH="x86"
		case $(uname -m) in
			x86_64)
				BITS="64"
			;;
			amd64)
				BITS="64"
			;;
			i386|i586|i686)
				BITS="32"
			;;
			*)
				echo "Could not detect bits for ${OS}, bailing out"
				exit 1
			;;
		esac
		if [ -z "$JAVA_HOME" ]; then
			JAVA_BIN=$(greadlink $(which java))
			JAVA_HOME=$(abspath $(dirname $JAVA_BIN)"/../../")
			TOOLS_JAR="${JAVA_HOME}/lib/tools.jar"
		else
			JAVA_BIN="$JAVA_HOME/bin/java"
			TOOLS_JAR="$JAVA_HOME/lib/tools.jar"
		fi
	;;
	macosx)
		ARCH="universal"
		if [[ "`sw_vers | grep 'ProductVersion:' | grep -o '[0-9]*\.[0-9]*\.[0-9]*'`" < "10.5.0" ]]; then
			BITS="32"
		else
			BITS="64"
		fi

		if [ -z "$JAVA_HOME" ]; then
			JAVA_BIN=$(greadlink `which java`)
			BASE=$(abspath "`dirname $JAVA_BIN`/../../")
			if [ "$BASE" == "/System/Library/Frameworks/JavaVM.framework/Versions" ]; then
				JAVA_HOME="${BASE}/CurrentJDK"
				TOOLS_JAR="$JAVA_HOME/Classes/classes.jar"
			else
				JAVA_HOME="${BASE}"
				TOOLS_JAR="${JAVA_HOME}/lib/tools.jar"
			fi
		else
			JAVA_BIN="$JAVA_HOME/bin/java"
			TOOLS_JAR="$JAVA_HOME/lib/tools.jar"
		fi
	;;
esac

TARGET="libTerminal.$SUFIX"
SOURCE="libTerminal-$OS-$ARCH-$BITS.$SUFIX"

rm -f lib/libTerminal.*
ln -s $SOURCE lib/$TARGET

ant -buildfile installer.xml build
LIBS=`echo ./lib/*.jar | tr ' ' ':'`
$JAVA_BIN -cp "$LIBS:$TOOLS_JAR" -Djava.library.path=lib -Dlog4j.configurationFile=log4j2-build.xml -Dincludes="src/*/plugin.xml,src/plugins/*/plugin.xml" org.drftpd.tools.installer.Wrapper "$@"
