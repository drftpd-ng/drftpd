#!/bin/sh
javah -jni -classpath ../../../../classes se.mog.io.FileSystem
javah -jni -classpath ../../../../classes se.mog.io.UnixFileSystem


gcc -g -fPIC -shared \
	-I${JAVA_HOME}/include \
	-I${JAVA_HOME}/include/linux \
	UnixFileSystem.c \
	-o libFileSystem.so
