#include <jni.h>
#include "se_mog_io_FileSystem.h"
#include "se_mog_io_WindowsFileSystem.h"

#include <windows.h>

JNIEXPORT jobject JNICALL Java_se_mog_io_FileSystem_getFileSystem
(JNIEnv *env, jobject obj) {
  jclass windowsFileSystemClass = (*env)->FindClass(env, "se/mog/io/WindowsFileSystem");
  if((*env)->ExceptionOccurred(env)) return;
  return (*env)->AllocObject(env, windowsFileSystemClass);  
}


JNIEXPORT jobject JNICALL Java_se_mog_io_WindowsFileSystem_getDiskFreeSpace
(JNIEnv *env, jobject obj, jobject fileObject) {

  jclass diskFreeSpaceClass = (*env)->FindClass(env, "se/mog/io/DiskFreeSpace");
  if((*env)->ExceptionOccurred(env)) return;

  jclass fileClass = (*env)->GetObjectClass(env, fileObject);
  jmethodID mid = (*env)->GetMethodID(env, fileClass, "getPath", "()Ljava/lang/String;");
  if (mid == 0) return;
  jstring jpath = (*env)->CallObjectMethod(env, fileObject, mid);

  const char *path = (*env)->GetStringUTFChars(env, jpath, 0);

   BOOL fResult;

      unsigned __int64 i64FreeBytesToCaller,
                       i64TotalBytes,
                       i64FreeBytes;

    fResult = GetDiskFreeSpaceExA (path,
                (PULARGE_INTEGER)&i64FreeBytesToCaller,
                (PULARGE_INTEGER)&i64TotalBytes,
                (PULARGE_INTEGER)&i64FreeBytes);

  (*env)->ReleaseStringUTFChars(env, jpath, path);

  jobject diskFreeSpace = (*env)->AllocObject(env, diskFreeSpaceClass);
  

  jfieldID fid;
  
  fid = (*env)->GetFieldID(env, diskFreeSpaceClass, "freeBytes", "J");
  (*env)->SetLongField(env, diskFreeSpace, fid, i64FreeBytesToCaller);

  fid = (*env)->GetFieldID(env, diskFreeSpaceClass, "totalBytes", "J");
  (*env)->SetLongField(env, diskFreeSpace, fid, i64TotalBytes);


  return diskFreeSpace;
}
