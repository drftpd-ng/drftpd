#include <jni.h>
#include "se_mog_io_FileSystem.h"
#include "se_mog_io_UnixFileSystem.h"

#include <sys/vfs.h>

JNIEXPORT jobject JNICALL Java_se_mog_io_FileSystem_getFileSystem
(JNIEnv *env, jobject obj) {
  jclass unixFileSystemClass = (*env)->FindClass(env, "se/mog/io/UnixFileSystem");
  if((*env)->ExceptionOccurred(env)) return;
  return (*env)->AllocObject(env, unixFileSystemClass);  
}


JNIEXPORT jobject JNICALL Java_se_mog_io_UnixFileSystem_getDiskFreeSpace
(JNIEnv *env, jobject obj, jobject fileObject) {

  jclass diskFreeSpaceClass = (*env)->FindClass(env, "se/mog/io/DiskFreeSpace");
  if((*env)->ExceptionOccurred(env)) return;

  jclass fileClass = (*env)->GetObjectClass(env, fileObject);
  jmethodID mid = (*env)->GetMethodID(env, fileClass, "getPath", "()Ljava/lang/String;");
  if (mid == 0) return;
  jstring jpath = (*env)->CallObjectMethod(env, fileObject, mid);

  const char *path = (*env)->GetStringUTFChars(env, jpath, 0);

  struct statfs buf;
  statfs(path, &buf);

  (*env)->ReleaseStringUTFChars(env, jpath, path);

  jobject diskFreeSpace = (*env)->AllocObject(env, diskFreeSpaceClass);
  

  jfieldID fid;
  
  fid = (*env)->GetFieldID(env, diskFreeSpaceClass, "freeBytes", "J");
  (*env)->SetLongField(env, diskFreeSpace, fid, (long  long)buf.f_bavail * (long  long)buf.f_bsize);

  fid = (*env)->GetFieldID(env, diskFreeSpaceClass, "totalBytes", "J");
  (*env)->SetLongField(env, diskFreeSpace, fid, (long  long)buf.f_blocks * (long  long)buf.f_bsize);


  return diskFreeSpace;
}
