/*
 * Created on 2003-maj-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.slave;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Vector;

import net.sf.drftpd.FatalException;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
//TODO SECURITY: check so that we never get a non-absolute path
public class RootBasket {
	private Collection roots;

	public RootBasket(Collection roots) throws FileNotFoundException {
		/** sanity checks **/
		validateRoots(roots);
		this.roots = new ArrayList(roots);
	}

	public Root getARoot() {
		long mostFree = 0;
		Root mostFreeRoot = null;
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
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

	/**
	 * Get a root for storing dir specified by dir
	 * @param dir DIRECTOTY to store file in
	 * @return
	 */
	public File getARootFile(String dir) {
		File file = new File(getARoot().getPath()+File.separator+dir);
		file.mkdirs();
		return file;
	}
	//Get root which has most of the tree structure that we have.
	public File getFile(String path) throws FileNotFoundException {
		return new File(getRootForFile(path).getPath()+File.separatorChar+path);
	}

	public Collection getMultipleFiles(String path)
		throws FileNotFoundException {
		Vector files = new Vector();
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = new File(root.getPath() + File.separatorChar + path);
			if (file.exists())
				files.add(file);
		}
		if (files.size() == 0)
			throw new FileNotFoundException("No files found");
		return files;
	}

	public Root getRootForFile(String path) throws FileNotFoundException {
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = new File(root.getPath() + File.separatorChar + path);
			if (file.exists())
				return root;
		}
		throw new FileNotFoundException(
			path + " not found in any root in the RootBasket");
		
	}
	//TODO check if paths are under different or same mount
	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable = 0;

		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File rootFile = root.getFile();
			totalDiskSpaceAvailable += rootFile.getDiskSpaceAvailable();
		}
		return totalDiskSpaceAvailable;
	}

	//TODO check if paths are under different or same mount
	public long getTotalDiskSpaceCapacity() {
		long totalDiskSpaceCapacity = 0;

		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File rootFile = root.getFile();
			totalDiskSpaceCapacity += rootFile.getDiskSpaceCapacity();
		}
		return totalDiskSpaceCapacity;
	}
	
	public Iterator iterator() {
		return roots.iterator();
	}

	//TODO check that no paths are under same mount or overlap each other.
	private void validateRoots(Collection roots) throws FileNotFoundException {
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Object o = iter.next();
			if (!(o instanceof Root))
				throw new ClassCastException(
					o.getClass().getName() + " is not net.sf.drftpd.slave.Root");
			Root root = (Root) o;
			File rootFile = root.getFile();
			if (!rootFile.exists())
				rootFile.mkdirs();
			if (!rootFile.exists())
				throw new FileNotFoundException("Invalid root: " + rootFile);
			for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
				Root root2 = (Root) iterator.next();
				if (root == root2)
					continue;
				if (root2.getPath().startsWith(root.getPath())) {
					throw new FatalException(
						"Overlapping roots: " + root + " and " + root2);
				}
			}
		}
	}
}
