/*
 * Created on 2003-maj-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.slave;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Iterator;
import java.util.StringTokenizer;
import java.util.Vector;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class RootBasket {
	private Vector roots;

	public RootBasket(String rootString) throws FileNotFoundException {
		Vector roots = new Vector();
		StringTokenizer st =
			new StringTokenizer(rootString, File.pathSeparator);
		while (st.hasMoreTokens()) {
			roots.add(new File(st.nextToken()));
		}
		validateRoots(roots);
		this.roots = roots;
	}
	public RootBasket(Collection roots) throws FileNotFoundException {
		/** sanity checks **/
		validateRoots(roots);
		/** check for overlapping roots **/
		this.roots = new Vector(roots);
	}

	private void validateRoots(Collection roots) throws FileNotFoundException {
		System.out.println("roots: " + roots);
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Object o = iter.next();
			if(!(o instanceof File)) throw new ClassCastException(o.getClass().getName()+" is not java.io.File");
			File root = (File) o;

			if(!root.exists()) throw new FileNotFoundException("Invalid root: "+root);
			for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
				File root2 = (File) iterator.next();
				if (root == root2)
					continue;
				if (root2.getPath().startsWith(root.getPath())) {
					throw new RuntimeException(
						"Overlapping roots: " + root + " and " + root2);
				}
			}
		}
	}
	public Iterator iterator() {
		return roots.iterator();
	}

	public File getARoot() {
		long mostFree = 0;
		File mostFreeRoot = null;
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			long diskSpaceAvailable = root.getDiskSpaceAvailable();
			if (diskSpaceAvailable > mostFree) {
				mostFree = diskSpaceAvailable;
				mostFreeRoot = root;
			}
		}
		if (mostFreeRoot == null)
			throw new RuntimeException("NoAvailableRootsException");
		return mostFreeRoot;
	}
	
	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable = 0;
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
		long totalDiskSpaceCapacity = 0;

		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			//			totalDiskSpaceAvailable += root.getDiskSpaceAvailable();
			totalDiskSpaceCapacity += root.getDiskSpaceCapacity();
		}
		return totalDiskSpaceCapacity;
	}
	public File getFile(String path) throws FileNotFoundException {
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root.getPath()+File.separatorChar+path);
			if(file.exists()) return file;
		}
		throw new FileNotFoundException(path+" not found in any root in the RootBasket");
	}
	
	public Collection getMultipleFiles(String path) throws FileNotFoundException {
		Vector files = new Vector();
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			File root = (File) iter.next();
			File file = new File(root.getPath()+File.separatorChar+path);
			if(file.exists()) files.add(file);
		}
		if(files.size() == 0) throw new FileNotFoundException("No files found");
		//return (File[])files.toArray(new File[] {});
		return files;
	}
}
