/*
 * Created on 2003-maj-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.slave;

import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import se.mog.io.File;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class RootBasket {
	private Vector roots;

	public RootBasket(String rootString) {
		Vector roots = new Vector();
		StringTokenizer st =
			new StringTokenizer(rootString, ",;:");
		while (st.hasMoreTokens()) {
			roots.add(st.nextToken());
		}
		
		this (roots);
	}
	public RootBasket(Collection roots) {
		/** sanity checks **/
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File)iter.next();
			if (!(root instanceof File)) throw new RuntimeException("Invalid types in RootBasket");
			
			for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
				File root2 = (File) iterator.next();
				if(root2.getPath().startsWith(root.getPath())) {
					throw new RuntimeException("Overlapping roots: "+root+" and "+root2);				
				}
			}
			
		}
		/** check for overlapping roots **/
		this.roots = new Vector(roots);
	}
	
	public Iterator iterator() {
		return roots.iterator();
	}
	
	public File getARoot() {
		long mostFree=0;
		File mostFreeRoot=null;
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			long diskSpaceAvailable = root.getDiskSpaceAvailable();
			if(diskSpaceAvailable > mostFree) {
				mostFree = diskSpaceAvailable;
				mostFreeRoot = root;
			}
		}
		if(mostFreeRoot == null) throw new RuntimeException("NoAvailableRootsException");
		return mostFreeRoot;
	}
	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable=0;
//		long totalDiskSpaceCapacity=0;
		
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			totalDiskSpaceAvailable += root.getDiskSpaceAvailable();
//			totalDiskSpaceCapacity += root.getDiskSpaceCapacity();
		}
		return totalDiskSpaceAvailable;
	}
	public long getTotalDiskSpaceCapacity() {
//		long totalDiskSpaceAvailable=0;
		long totalDiskSpaceCapacity=0;
		
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
//			totalDiskSpaceAvailable += root.getDiskSpaceAvailable();
			totalDiskSpaceCapacity += root.getDiskSpaceCapacity();
		}
		return totalDiskSpaceCapacity;
	}
}
