/*
 * Created on 2003-maj-06
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package se.mog.io;

class DiskFreeSpace {
	long freeBytes;
	long totalBytes;
	public String toString() {
		return "freeBytes:\t"+freeBytes+"\ntotalBytes:\t"+totalBytes;
	}
}
