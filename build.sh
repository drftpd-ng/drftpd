#!/bin/sh
# $Id$
$ANT_HOME/bin/ant -buildfile installer.xml clean build
LIBS=`echo ./lib/*.jar $ANT_HOME/lib/*.jar | tr ' ' ':'`
$JAVA_HOME/bin/java -cp "$LIBS:$JAVA_HOME/lib/tools.jar" -Djava.library.path=lib/ -Dlog4j.configuration=file:log4j-build.properties -Dincludes="src/*/plugin.xml,src/plugins/*/plugin.xml" org.drftpd.tools.installer.Wrapper
