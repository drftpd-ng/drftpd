#!/bin/sh
javah -jni -classpath ../../../../classes se.mog.io.FileSystem
javah -jni -classpath ../../../../classes se.mog.io.UnixFileSystem


gcc -g -fPIC -shared \
	-I/opt/sun-jdk-1.4.1.02/include \
	-I/opt/sun-jdk-1.4.1.02/include/linux \
	UnixFileSystem.c \
	-o libFileSystem.so
