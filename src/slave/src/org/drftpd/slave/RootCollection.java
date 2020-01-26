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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.io.PhysicalFile;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author mog
 * @version $Id$
 */
public class RootCollection {
	private static final Logger logger = LogManager.getLogger(RootCollection.class);

	private ArrayList<Root> _roots = null;
	private Slave _slave = null;
	private ThreadPoolExecutor _pool;

	public RootCollection(Slave slave, Collection<Root> roots) throws IOException {
		/** sanity checks * */
		validateRoots(roots);
		_roots = new ArrayList<>(roots);
		_slave = slave;
		if (_slave.concurrentRootIteration()) {
			int numThreads = Math.min(_roots.size(), Runtime.getRuntime().availableProcessors());
			_pool = new ThreadPoolExecutor(numThreads, numThreads, 300, TimeUnit.SECONDS,
                    new LinkedBlockingQueue<>(), new RootListHandlerThreadFactory(),
					new ThreadPoolExecutor.CallerRunsPolicy());
			_pool.allowCoreThreadTimeOut(true);
		}
	}

	/**
	 * Returns a sorted (alphabetical) list of inodes in the path given
	 * @param path
	 * @return
	 */
	public TreeSet<String> getLocalInodes(String path) {
		TreeSet<String> files = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
		for (Root root : _roots) {
			String[] fileArray = root.getFile(path).list();
			if (fileArray == null) continue;
			files.addAll(Arrays.asList(fileArray));
		}
		return files;
	}

	/**
	 * Returns a sorted (alphabetical) list of inodes in the path given along with
	 * a file object pointing to the file and the most recent last modified for the
	 * path across the root collection
	 * @param path
	 * @return
	 */
	public RootPathContents getLocalInodesConcurrent(String path) {
		CountDownLatch latch = new CountDownLatch(_roots.size());
		File[][] rootFiles = new File[_roots.size()][];
		Long[] rootLastModified = new Long[_roots.size()];
		TreeMap<String,File> files = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
		for (int i = 0; i < _roots.size(); i++) {
			_pool.execute(new RootListHandler(rootFiles, i, latch, path, rootLastModified));
		}
		while (true) {
			try {
				latch.await();
				break;
			} catch (InterruptedException e) {
				// Loop around and wait again
			}
		}
		long lastModified = Long.MIN_VALUE;
		for (int i = 0; i < _roots.size(); i++) {
			if (rootFiles[i] != null) {
				for (int j = 0; j < rootFiles[i].length; j++) {
					if (!files.containsKey(rootFiles[i][j].getName())) {
						files.put(rootFiles[i][j].getName(), rootFiles[i][j]);
					}
				}
			}
			if (rootLastModified[i] != null) {
				if (rootLastModified[i] > lastModified) {
					lastModified = rootLastModified[i];
				}
			}
		}
		return new RootPathContents(lastModified, files);
	}

	public long getLastModifiedForPath(String path) {
		long lastModified = Long.MIN_VALUE;
		for (Root root : _roots) {
			long rootLastModified = root.getFile(path).lastModified();
			if (rootLastModified > lastModified) {
				lastModified = rootLastModified;
			}
		}
		return lastModified;
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

		PhysicalFile file = bestRoot.getFile(dir);
		file.mkdirs2();

		return file;
	}

	// Get root which has most of the tree structure that we have.
	public PhysicalFile getFile(String path) throws FileNotFoundException {
		return new PhysicalFile(getRootForFile(path).getPath() + File.separatorChar
				+ path);
	}

	public List<File> getMultipleFiles(String path) throws FileNotFoundException {
		ArrayList<File> files = new ArrayList<>();

		for (Root r : getMultipleRootsForFile(path)) {
			files.add(r.getFile(path));
		}
		return files;
	}

	public List<Root> getMultipleRootsForFile(String path)
			throws FileNotFoundException {
		ArrayList<Root> roots = new ArrayList<>();

		
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
			File file = new File(root.getPath() + PhysicalFile.separatorChar + path);
			if (file.exists()) {
				return root;
			}
		}
		throw new FileNotFoundException(path + " wasn't found in any root");
	}

	public long getTotalDiskSpaceAvailable() {
		long totalDiskSpaceAvailable = 0;

		for (Root root : _roots) {
			totalDiskSpaceAvailable += root.getDiskSpaceAvailable();
		}

		return totalDiskSpaceAvailable;
	}

	public long getTotalDiskSpaceCapacity() {
		long totalDiskSpaceCapacity = 0;

		for (Root root : _roots) {
			totalDiskSpaceCapacity += root.getDiskSpaceCapacity();
		}

		return totalDiskSpaceCapacity;
	}

	public Iterator<Root> iterator() {
		return _roots.iterator();
	}

	private static void validateRoots(Collection<Root> roots) throws IOException {
		File[] mountsArr = File.listRoots();
		ArrayList<File> mounts = new ArrayList<>(mountsArr.length);

        for (File aMountsArr : mountsArr) {
            mounts.add(aMountsArr);
        }

		mounts.sort(new Comparator<File>() {
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

                return (Integer.compare(anotherVal, thisVal));
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
			
			Hashtable<String, Object> usedMounts = new Hashtable<>();

			for (File mount : mounts) {

				if (fullpath.startsWith(mount.getPath())) {
                    logger.info("{} in mount {}", fullpath, mount.getPath());

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
		return _roots;
	}

	private class RootListHandler implements Runnable {
		
		private File[][] _files;
		private int _root;
		private CountDownLatch _latch;
		private String _path;
		private Long[] _rootLastModified;
		
		public RootListHandler(File[][] files, int root, CountDownLatch latch, String path, Long[] rootLastModified) {
			_files = files;
			_root = root;
			_latch = latch;
			_path = path;
			_rootLastModified = rootLastModified;
		}

		public void run() {
			Thread currThread = Thread.currentThread();
			currThread.setName("Root List Handler - " + currThread.getId()
					+ " - processing root " + _root + " - " + _path);
			File rootPath = _roots.get(_root).getFile(_path);
			if (rootPath.exists()) {
				_files[_root] = rootPath.listFiles();
				_rootLastModified[_root] = rootPath.lastModified();
			}
			_latch.countDown();
			currThread.setName(RootListHandlerThreadFactory.getIdleThreadName(currThread.getId()));
		}
	}
}

class RootListHandlerThreadFactory implements ThreadFactory {
	public static String getIdleThreadName(long threadId) {
		return "Root List Handler - "+ threadId + " - Waiting for root to process";
	}

	public Thread newThread(Runnable r) {
		Thread t = Executors.defaultThreadFactory().newThread(r);
		t.setName(getIdleThreadName(t.getId()));
		return t;
	}	
}
