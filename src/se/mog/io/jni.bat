javah -jni -classpath ../../.. se.mog.io.FileSystem
javah -jni -classpath ../../.. se.mog.io.WindowsFileSystem

gcc -g -I%JAVA_HOME%/include -I%JAVA_HOME%/include/win32 -c -g WindowsFileSystem.c
dllwrap --output-def FileSystem.def --add-stdcall-alias -oFileSystem.dll -s WindowsFileSystem.o
