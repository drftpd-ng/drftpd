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
package net.sf.drftpd.remotefile;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.InvalidDirectoryException;
import net.sf.drftpd.slave.Root;
import net.sf.drftpd.slave.RootBasket;


/**
 * A wrapper for java.io.File to the net.sf.drftpd.RemoteFile structure.
 *
 * @author mog
 * @version $Id: FileRemoteFile.java,v 1.40 2004/11/02 07:32:47 zubov Exp $
 */
public class FileRemoteFile extends AbstractRemoteFile {
    RootBasket rootBasket;
    String path;
    private boolean isFile;
    private boolean isDirectory;
    private long length;
    private long lastModified;
    Hashtable filefiles;

    public FileRemoteFile(RootBasket rootBasket) throws IOException {
        this(rootBasket, "");
    }

    public FileRemoteFile(RootBasket rootBasket, String path)
        throws IOException {
        //if(path.length() != 0) {
        //	if(path.charAt(path.length()-1) == File.separatorChar) path = path.substring(0, path.length()-1);
        //}
        this.path = path;
        this.rootBasket = rootBasket;

        //this.slaves = slaves;
        //check that the roots in the rootBasket are in sync
        boolean first = true;

        for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();
            File file = root.getFile(path);

            if (!file.exists()) {
                continue;
            }

            //			if(file.isDirectory() && file.list().length == 0) {
            //				while(file.list().length == 0) {
            //					File file2 = file.getParentFile();
            //					file.delete();
            //					file = file2;
            //				}
            //				continue;
            //			}
            if (first) {
                first = false;
                isDirectory = file.isDirectory();
                isFile = file.isFile();

                if ((!isFile() && !isDirectory()) || (isFile() && isDirectory)) {
                    throw new IOException(
                        "(!isFile() && !isDirectory()) || (isFile() && isDirectory): " +
                        isFile() + isDirectory() + " " + path);
                }

                if (isDirectory()) {
                    length = 0;
                } else {
                    length = file.length();
                }

                lastModified = file.lastModified();
            } else {
                if (file.isDirectory() != isDirectory) {
                    throw new IOException(
                        "roots are out of sync, dir&file mix: " + path);
                }

                if (file.isFile() != isFile) {
                    throw new IOException(
                        "roots are out of sync, file&dir mix: " + path);
                }

                if (isFile) {
                    throw new IOException("File collision: " + path);
                }

                //if(isFile() && file.length() != length) throw new IOException("roots are out of sync, different sizes: "+path);
            }

            if (!file.getCanonicalPath().equalsIgnoreCase(file.getAbsolutePath())) {
                throw new InvalidDirectoryException("Not following symlink: " +
                    file.getAbsolutePath());
            }
        }
    }

    public String getName() {
        return path.substring(path.lastIndexOf(File.separatorChar) + 1);
    }

    public String getParent() {
        throw new UnsupportedOperationException();

        //return file.getParent();
    }

    public String getPath() {
        return path;

        //throw new UnsupportedOperationException();
        //return file.getPath();
    }

    public String getGroupname() {
        return "drftpd";
    }

    public String getUsername() {
        return "drftpd";
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public boolean isFile() {
        return isFile;
    }

    public long lastModified() {
        return lastModified;
    }

    public long length() {
        return length;
    }

    /**
     * Returns an array of FileRemoteFile:s representing the contents of the directory this FileRemoteFile represents.
     */
    public RemoteFileInterface[] listFiles() {
        return (RemoteFileInterface[]) getFiles().toArray(new FileRemoteFile[0]);
    }

    private void buildFileFiles() {
        if (filefiles != null) {
            return;
        }

        filefiles = new Hashtable();

        if (!isDirectory()) {
            throw new IllegalArgumentException(
                "listFiles() called on !isDirectory()");
        }

        for (Iterator iter = rootBasket.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();
            File file = new File(root.getPath() + "/" + path);

            if (!file.exists()) {
                continue;
            }

            if (!file.isDirectory()) {
                throw new FatalException(file.getPath() +
                    " is not a directory, attempt to getFiles() on it");
            }

            if (!file.canRead()) {
                throw new FatalException("Cannot read: " + file);
            }

            File[] tmpFiles = file.listFiles();

            //returns null if not a dir, blah!
            if (tmpFiles == null) {
                throw new NullPointerException("list() on " + file +
                    " returned null");
            }

            for (int i = 0; i < tmpFiles.length; i++) {
                try {
                    if (tmpFiles[i].isDirectory() && isEmpty(tmpFiles[i])) {
                        continue;
                    }

                    FileRemoteFile listfile = new FileRemoteFile(rootBasket,
                            path + File.separatorChar + tmpFiles[i].getName());
                    filefiles.put(tmpFiles[i].getName(), listfile);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        if (!getName().equals("") && filefiles.isEmpty()) {
            throw new RuntimeException(
                "Empty (not-root) directory, shouldn't happen");
        }
    }

    /**
     * @see net.sf.drftpd.remotefile.AbstractRemoteFile#getFiles()
     */
    public Collection getFiles() {
        buildFileFiles();

        return filefiles.values();
    }

    /**
     * @return true if directory contained no files and is now deleted, false otherwise.
     */
    private static boolean isEmpty(File dir) {
        File[] listfiles = dir.listFiles();

        if (listfiles == null) {
            throw new FatalException("Not a directory or IO error: " + dir);
        }

        for (int i = 0; i < listfiles.length; i++) {
            File file = listfiles[i];

            if (file.isFile()) {
                return false;
            }
        }

        for (int i = 0; i < listfiles.length; i++) {
            File file = listfiles[i];

            // parent directory not empty
            if (!isEmpty(file)) {
                return false;
            }
        }

        dir.delete();

        return true;
    }

    public Collection getSlaves() {
        return new ArrayList();
    }

    public boolean isDeleted() {
        return false;
    }
}
