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
import java.util.StringTokenizer;
import java.util.Vector;

import net.sf.drftpd.FatalException;

import se.mog.io.File;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class RootBasket {
	private Collection roots;

	public RootBasket(String rootString) throws FileNotFoundException {
		ArrayList roots = new ArrayList();
		StringTokenizer st =
			new StringTokenizer(rootString, File.pathSeparator);
		while (st.hasMoreTokens()) {
			roots.add(new Root(st.nextToken(), 0L, 0));
		}
		validateRoots(roots);
		this.roots = roots;
	}

	public RootBasket(Collection roots) throws FileNotFoundException {
		/** sanity checks **/
		validateRoots(roots);
		/** check for overlapping roots **/
		this.roots = new ArrayList(roots);
	}

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
				File root2File = root2.getFile();
				if (rootFile == root2File)
					continue;
				if (root2File.getPath().startsWith(rootFile.getPath())) {
					throw new FatalException(
						"Overlapping roots: " + rootFile + " and " + root2File);
				}
			}
		}
	}
	
	public Iterator iterator() {
		return roots.iterator();
	}

	//TODO Use net.sf.drftpd.Root for getting the right root.
	public File getARoot() {
		long mostFree = 0;
		File mostFreeRoot = null;
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File rootFile = root.getFile();
			long diskSpaceAvailable = rootFile.getDiskSpaceAvailable();
			if (diskSpaceAvailable > mostFree) {
				mostFree = diskSpaceAvailable;
				mostFreeRoot = rootFile;
			}
		}
		if (mostFreeRoot == null)
			throw new RuntimeException("NoAvailableRootsException");
		return mostFreeRoot;
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

	public File getFile(String path) throws FileNotFoundException {
		for (Iterator iter = roots.iterator(); iter.hasNext();) {
			Root root = (Root) iter.next();
			File file = new File(root.getPath() + File.separatorChar + path);
			if (file.exists())
				return file;
		}
		throw new FileNotFoundException(
			path + " not found in any root in the RootBasket");
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
}
