#!/bin/sh
javah -jni -classpath ../../../../classes se.mog.io.FileSystem
javah -jni -classpath ../../../../classes se.mog.io.UnixFileSystem

cc -bundle -I/System/Library/Frameworks/JavaVM.framework/Headers \
	-o libUnixFileSystem.jnilib -framework JavaVM \
	UnixFileSystem.c
