/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.slave;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import se.mog.io.File;

/**
 * @author mog
 * @version $Id$
 */
public class RootCollection {
	private static final Logger logger = Logger.getLogger(RootCollection.class);

	private Collection<Root> _roots = null;
	private Slave _slave = null;

	public RootCollection(Slave slave, Collection<Root> roots) throws IOException {
		/** sanity checks * */
		validateRoots(roots);
		_roots = new ArrayList<Root>(roots);
		_slave = slave;
	}
	
	/**
	 * Returns a sorted (alphabetical) list of inodes in the path given
	 * @param path
	 * @return
	 */
	public TreeSet<String> getLocalInodes(String path) {
		TreeSet<String> files = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		for (Root root : _roots) {
			String[] fileArray = root.getFile(path).list();
			if (fileArray == null) continue;
			files.addAll(Arrays.asList(fileArray));
		}
		return files;
	}

	public Root getARoot() {
		long mostFree = 0;
		Root mostFreeRoot = null;

		for (Root root : _roots) {
			long diskSpaceAvailable = root.getDiskSpaceAvailable();

			if (diskSpaceAvailable > mostFree) {
				mostFree = diskSpaceAvailable;
				mostFreeRoot = root;
			}
		}

		if (mostFreeRoot == null) {
			throw new RuntimeException("NoAvailableRootsException");
		}

		return mostFreeRoot;
	}

	/**
	 * Get a directory specified by dir under an approperiate root for storing
	 * storing files in.
	 * 
	 * @param directory
	 *            to store file in
	 * @throws IOException
	 */
	public File getARootFileDir(String dir) throws IOException {
		Root bestRoot = _slave.getDiskSelection().getBestRoot(dir);

		// to avoid this error SlaveSelectionManager MUST work
		// synchronized with DiskSelection.
		if (bestRoot == null) {
			throw new IOException("No suitable root was found.");
		}
		bestRoot.touch();

		File file = bestRoot.getFile(dir);
		file.mkdirs2();

		return file;
	}

	// Get root which has most of the tree structure that we have.
	public File getFile(String path) throws FileNotFoundException {
		return new File(getRootForFile(path).getPath() + File.separatorChar
				+ path);
	}

	/**
	 * Returns an ArrayList containing se.mog.io.File objects
	 */
	public List<File> getMultipleFiles(String path) throws FileNotFoundException {
		ArrayList<File> files = new ArrayList<File>();

		for (Root r : getMultipleRootsForFile(path)) {
			files.add(r.getFile(path));
		}

		return files;
	}

	public List<Root> getMultipleRootsForFile(String path)
			throws FileNotFoundException {
		ArrayList<Root> roots = new ArrayList<Root>();

		
		for (Root r : _roots) {
			if (r.getFile(path).exists()) {
				roots.add(r);
			}
		}
		for (Root root : _roots) {

			if (root.getFile(path).exists()) {
				roots.add(root);
			}
		}

		if (roots.size() == 0) {
			throw new FileNotFoundException("Unable to find suitable root: "
					+ path);
		}

		return roots;
	}

	public Root getRootForFile(String path) throws FileNotFoundException {
		for (Root root : _roots) {
			File file = new File(root.getPath() + File.separatorChar + path);
			if (file.exists()) {
				return root;
			}
		}
		throw new FileNotFoundException(path + " wasn't found in any root");
	}

	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable = 0;

		for (Root root : _roots) {
			File rootFile = root.getFile();
			totalDiskSpaceAvailable += rootFile.getDiskSpaceAvailable();
		}

		return totalDiskSpaceAvailable;
	}

	public long getTotalDiskSpaceCapacity() {
		long totalDiskSpaceCapacity = 0;

		for (Root root : _roots) {
			File rootFile = root.getFile();
			totalDiskSpaceCapacity += rootFile.getDiskSpaceCapacity();
		}

		return totalDiskSpaceCapacity;
	}

	public Iterator<Root> iterator() {
		return _roots.iterator();
	}

	private static void validateRoots(Collection<Root> roots) throws IOException {
		File[] mountsArr = File.listMounts();
		ArrayList<File> mounts = new ArrayList<File>(mountsArr.length);

		for (int i = 0; i < mountsArr.length; i++) {
			mounts.add(mountsArr[i]);
		}

		Collections.sort(mounts, new Comparator<File>() {
			public boolean equals(Object obj) {
				if (obj == null) return false;
				return obj.getClass() == getClass();
			}

			public int hashCode() {
				return getClass().hashCode();
			}

			public int compare(File o1, File o2) {
				int thisVal = o1.getPath().length();
				int anotherVal = o2.getPath().length();

				return ((thisVal < anotherVal) ? 1
						: ((thisVal == anotherVal) ? 0 : (-1)));
			}
		});

		for (Root root : roots) {

			File rootFile = root.getFile();

			if (!rootFile.exists()) {
				if (!rootFile.mkdirs()) {
					throw new IOException("mkdirs() failed on "
							+ rootFile.getPath());
				}
			}

			if (!rootFile.exists()) {
				throw new FileNotFoundException("Invalid root: " + rootFile);
			}

			String fullpath = rootFile.getAbsolutePath();
			
			Hashtable<String, Object> usedMounts = new Hashtable<String, Object>();

			for (File mount : mounts) {

				if (fullpath.startsWith(mount.getPath())) {
					logger.info(fullpath + " in mount " + mount.getPath());

					if (usedMounts.get(mount.getPath()) != null) {
						throw new IOException("Multiple roots in mount "
								+ mount.getPath());
					}

					usedMounts.put(mount.getPath(), new Object());

					break;
				}
			}

			for (Root root2 : roots) {

				if (root == root2) {
					continue;
				}

				if ((root2.getPath() + File.pathSeparator).startsWith(root
						.getPath()
						+ File.pathSeparator)) {
					throw new RuntimeException("Overlapping roots: "
							+ root.getPath() + " and " + root2.getPath());
				}
			}
		}
	}

	public int getMaxPath() {
		if (Slave.isWin32) {
			int maxPath = 0;

			for (Root root : _roots) {
				maxPath = Math.max(root.getPath().length(), maxPath);
			} // constant for win32, see

			// http://support.microsoft.com/default.aspx?scid=http://support.microsoft.com:80/support/kb/articles/Q177/6/65.ASP&NoWebContent=1
			// for more info
			return 256 - maxPath;
		}

		return -1;
	}

	public ArrayList<Root> getRootList() {
		return (ArrayList<Root>) _roots;
	}
}
