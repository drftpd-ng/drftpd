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
package net.sf.drftpd.slave;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.PermissionDeniedException;

import org.apache.log4j.Logger;

import se.mog.io.File;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;


//TODO SECURITY: verify so that we never get outside of a rootbasket root

/**
 * @author mog
 * @version $Id: RootBasket.java,v 1.29 2004/08/03 20:14:03 zubov Exp $
 */
public class RootBasket {
    private static final Logger logger = Logger.getLogger(RootBasket.class);
    private Collection _roots;

    public RootBasket(Collection roots) throws IOException {
        /** sanity checks **/
        validateRoots(roots);
        _roots = new ArrayList(roots);
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

        if (mostFreeRoot == null) {
            throw new RuntimeException("NoAvailableRootsException");
        }

        return mostFreeRoot;
    }

    /**
     * Get a directory specified by dir under an approperiate root for storing storing files in.
     * @param directory to store file in
     * @throws PermissionDeniedException If creation of dir failed in the slave root selected by getARootFileDir().
     */
    public File getARootFileDir(String dir) throws PermissionDeniedException {
        Iterator iter = _roots.iterator();
        Root bestRoot = (Root) iter.next();

        while (iter.hasNext()) {
            Root root = (Root) iter.next();

            if (bestRoot.isFull()) {
                bestRoot = root;

                continue;
            }

            if (root.lastModified() > bestRoot.lastModified()) {
                if (root.isFull() && !bestRoot.isFull()) {
                    continue;
                }

                bestRoot = root;
            }
        }

        bestRoot.touch();

        File file = bestRoot.getFile(dir);

        if (!file.exists()) {
            if (!file.mkdirs()) {
                throw new PermissionDeniedException("mkdirs failed on " +
                    file.getPath());
            }
        }

        return file;
    }

    //Get root which has most of the tree structure that we have.
    public File getFile(String path) throws FileNotFoundException {
        return new File(getRootForFile(path).getPath() + File.separatorChar +
            path);
    }

    /**
     * Returns an ArrayList containing se.mog.io.File objects
     */
    public List getMultipleFiles(String path) throws FileNotFoundException {
        ArrayList files = new ArrayList();

        for (Iterator iter = getMultipleRootsForFile(path).iterator();
                iter.hasNext();) {
            files.add(((Root) iter.next()).getFile(path));
        }

        return files;
    }

    public List getMultipleRootsForFile(String path)
        throws FileNotFoundException {
        ArrayList roots = new ArrayList();

        for (Iterator iter = _roots.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();

            if (root.getFile(path).exists()) {
                roots.add(root);
            }
        }

        if (roots.size() == 0) {
            throw new FileNotFoundException("Unable to find suitable root");
        }

        return roots;
    }

    public Root getRootForFile(String path) throws FileNotFoundException {
        for (Iterator iter = _roots.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();
            File file = new File(root.getPath() + File.separatorChar + path);

            if (file.exists()) {
                return root;
            }
        }

        throw new FileNotFoundException(path + " wasn't found in any root");
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

    private static void validateRoots(Collection roots)
        throws IOException {
        File[] mountsArr = File.listMounts();
        ArrayList mounts = new ArrayList(mountsArr.length);

        for (int i = 0; i < mountsArr.length; i++) {
            mounts.add(mountsArr[i]);
        }

        Collections.sort(mounts,
            new Comparator() {
                public boolean equals(Object obj) {
                    return obj.getClass() == getClass();
                }

                public int hashCode() {
                    return getClass().hashCode();
                }

                public int compare(Object o1, Object o2) {
                    return compare((File) o1, (File) o2);
                }

                public int compare(File o1, File o2) {
                    int thisVal = o1.getPath().length();
                    int anotherVal = o2.getPath().length();

                    return ((thisVal < anotherVal) ? 1
                                                   : ((thisVal == anotherVal)
                    ? 0 : (-1)));
                }
            });

        Hashtable usedMounts = new Hashtable();

        for (Iterator iter = roots.iterator(); iter.hasNext();) {
            Object o = iter.next();

            if (!(o instanceof Root)) {
                throw new RuntimeException();
            }

            Root root = (Root) o;
            File rootFile = root.getFile();

            if (!rootFile.exists()) {
                if (!rootFile.mkdirs()) {
                    throw new IOException("mkdirs() failed on " +
                        rootFile.getPath());
                }
            }

            if (!rootFile.exists()) {
                throw new FileNotFoundException("Invalid root: " + rootFile);
            }

            String fullpath = rootFile.getAbsolutePath();

            for (Iterator iterator = mounts.iterator(); iterator.hasNext();) {
                File mount = (File) iterator.next();

                if (fullpath.startsWith(mount.getPath())) {
                    logger.info(fullpath + " in mount " + mount.getPath());

                    if (usedMounts.get(mount.getPath()) != null) {
                        throw new IOException("Multiple roots in mount " +
                            mount.getPath());
                    }

                    usedMounts.put(mount.getPath(), new Object());

                    break;
                }
            }

            for (Iterator iterator = roots.iterator(); iterator.hasNext();) {
                Root root2 = (Root) iterator.next();

                if (root == root2) {
                    continue;
                }

                if (root2.getPath().startsWith(root.getPath())) {
                    throw new FatalException("Overlapping roots: " +
                        root.getPath() + " and " + root2.getPath());
                }
            }
        }
    }

    public int getMaxPath() {
        if (SlaveImpl.isWin32) {
            int maxPath = 0;

            for (Iterator iter = iterator(); iter.hasNext();) {
                Root root = (Root) iter.next();
                maxPath = Math.max(root.getPath().length(), maxPath);
            } //constant for win32, see

            //http://support.microsoft.com/default.aspx?scid=http://support.microsoft.com:80/support/kb/articles/Q177/6/65.ASP&NoWebContent=1
            // for more info
            return 256 - maxPath;
        }

        return -1;
    }
}
