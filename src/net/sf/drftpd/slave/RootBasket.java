/*
 * Created on 2003-maj-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.slave;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.FatalException;

import org.apache.log4j.Logger;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
//TODO SECURITY: verify so that we never get outside of a rootbasket root
public class RootBasket {
	private static Logger logger = Logger.getLogger(RootBasket.class);
	private Collection _roots;

	public RootBasket(Collection roots) throws IOException {
		/** sanity checks **/
		validateRoots(roots);
		this._roots = new ArrayList(roots);
	}

	public Root getARoot() {
		long mostFree = 0;
		Root mostFreeRoot = null;
		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
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
	 * Get a directory specified by dir under an approperiate root for storing storing files in.
	 * @param directory to store file in
	 * @return
	 */
	public File getARootFileDir(String dir) {
		Iterator iter = _roots.iterator();
		Root bestRoot = (Root) iter.next();
		while (iter.hasNext()) {
			Root root = (Root) iter.next();
			if(bestRoot.isFull()) {
				bestRoot = root;
				continue;
			}
			if(root.lastModified() > bestRoot.lastModified()) {
				if(root.isFull() && !bestRoot.isFull()) continue;
				bestRoot = root;
			}
		}
		bestRoot.touch();
		File file = bestRoot.getFile(dir);
		file.mkdirs();
		return file;
	}
	//Get root which has most of the tree structure that we have.
	public File getFile(String path) throws FileNotFoundException {
		return new File(
			getRootForFile(path).getPath() + File.separatorChar + path);
	}

	/**
	 * Returns an ArrayList containing se.mog.io.File objects
	 */
	public List getMultipleFiles(String path) throws FileNotFoundException {
		ArrayList files = new ArrayList();
		for (Iterator iter = getMultipleRootsForFile(path).iterator(); iter.hasNext();) {
			files.add(((Root) iter.next()).getFile(path));
		}
		return files;
	}

	public List getMultipleRootsForFile(String path)
		throws FileNotFoundException {
		ArrayList roots = new ArrayList();
		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			if (root.getFile(path).exists())
				roots.add(root);
		}
		if (roots.size() == 0)
			throw new FileNotFoundException("No files found");
		return roots;
	}

	public Root getRootForFile(String path) throws FileNotFoundException {
		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = new File(root.getPath() + File.separatorChar + path);
			if (file.exists())
				return root;
		}
		throw new FileNotFoundException(
			path + " not found in any root in the RootBasket");

	}

	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable = 0;

		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File rootFile = root.getFile();
			totalDiskSpaceAvailable += rootFile.getDiskSpaceAvailable();
		}
		return totalDiskSpaceAvailable;
	}

	public long getTotalDiskSpaceCapacity() {
		long totalDiskSpaceCapacity = 0;

		for (Iterator iter = _roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File rootFile = root.getFile();
			totalDiskSpaceCapacity += rootFile.getDiskSpaceCapacity();
		}
		return totalDiskSpaceCapacity;
	}

	public Iterator iterator() {
		return _roots.iterator();
	}

	private static void validateRoots(Collection roots) throws IOException {
		File mountsArr[] = File.listMounts();
		ArrayList mounts = new ArrayList(mountsArr.length);
		for (int i = 0; i < mountsArr.length; i++) {
			mounts.add(mountsArr[i]);
		}
		Collections.sort(mounts, new Comparator() {
			public boolean equals(Object obj) {
				return obj.getClass() == this.getClass();
			}

			public int compare(Object o1, Object o2) {
				return compare((File) o1, (File) o2);
			}
			public int compare(File o1, File o2) {
				int thisVal = o1.getPath().length();
				int anotherVal = o2.getPath().length();
				return (
					thisVal < anotherVal
						? 1
						: (thisVal == anotherVal ? 0 : -1));
			}
		});

		Hashtable usedMounts = new Hashtable();
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Object o = iter.next();
			assert o instanceof Root;

			Root root = (Root) o;
			File rootFile = root.getFile();
			if (!rootFile.exists()) {
				if (!rootFile.mkdirs()) {
					throw new IOException(
						"mkdirs() failed on " + rootFile.getPath());
				}
			}
			if (!rootFile.exists())
				throw new FileNotFoundException("Invalid root: " + rootFile);

			String fullpath = rootFile.getAbsolutePath();

			for (Iterator iterator = mounts.iterator(); iterator.hasNext();) {
				File mount = (File) iterator.next();
				if (fullpath.startsWith(mount.getPath())) {
					logger.info(fullpath + " in mount " + mount.getPath());
					if (usedMounts.get(mount.getPath()) != null) {
						throw new IOException(
							"Multiple roots in mount " + mount.getPath());
					}
					usedMounts.put(mount.getPath(), new Object());
					break;
				}
			}

			for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
				Root root2 = (Root) iterator.next();
				if (root == root2)
					continue;
				if (root2.getPath().startsWith(root.getPath())) {
					throw new FatalException(
						"Overlapping roots: "
							+ root.getPath()
							+ " and "
							+ root2.getPath());
				}
			}
		}
	}
}
