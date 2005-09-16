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


import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.zip.CRC32;
import java.util.zip.CheckedInputStream;

import org.apache.log4j.Logger;
import org.drftpd.remotefile.AbstractLightRemoteFile;
import org.drftpd.remotefile.LightRemoteFileInterface;


/**
 * A wrapper for java.io.File to the net.sf.drftpd.RemoteFile structure.
 *
 * @author mog
 * @version $Id$
 * @deprecated
 */
public class FileRemoteFile extends AbstractLightRemoteFile {
    private static final Logger logger = Logger.getLogger(FileRemoteFile.class);
    Hashtable _filefiles;
    String _path;
    RootCollection _roots;
    private boolean isDirectory;
    private boolean isFile;
    private long lastModified;
    private long length;

    public FileRemoteFile(RootCollection rootBasket) throws IOException {
        this(rootBasket, "");
    }

    public FileRemoteFile(RootCollection roots, String path)
        throws IOException {
        _path = path;
        _roots = roots;

        List files = roots.getMultipleFiles(path);
        File firstFile;
        // sanity checking
        {
            {
                Iterator iter = files.iterator();
                firstFile = (File) iter.next();

                isFile = firstFile.isFile();
                isDirectory = firstFile.isDirectory();

                if ((isFile && isDirectory) || (!isFile && !isDirectory)) {
                    throw new IOException("isFile && isDirectory: " + path);
                }

                checkSymlink(firstFile);

                while (iter.hasNext()) {
                    File file = (File) iter.next();
                    checkSymlink(file);

                    if ((isFile != file.isFile()) ||
                            (isDirectory != file.isDirectory())) {
                        throw new IOException(
                            "roots are out of sync, file&dir mix: " + path);
                    }
                }
            }

            if (isFile && (files.size() > 1)) {
                ArrayList checksummers = new ArrayList(files.size());

                for (Iterator iter = files.iterator(); iter.hasNext();) {
                    File file = (File) iter.next();
                    Checksummer checksummer = new Checksummer(file);
                    checksummer.start();
                    checksummers.add(checksummer);
                }

                while (true) {
                    boolean waiting = false;

                    for (Iterator iter = checksummers.iterator();
                            iter.hasNext();) {
                        Checksummer cs = (Checksummer) iter.next();

                        if (cs.isAlive()) {
                            waiting = true;

                            try {
                                synchronized (cs) {
                                    cs.wait(10000); // wait a max of 10 seconds
													// race condition could
													// occur between above
													// isAlive() statement and
													// when the Checksummer
													// finishes
                                }
                            } catch (InterruptedException e) {
                            }

                            break;
                        }
                    }

                    if (!waiting) {
                        break;
                    }
                }

                Iterator iter = checksummers.iterator();
                long checksum = ((Checksummer) iter.next()).getCheckSum()
                                 .getValue();

                for (; iter.hasNext();) {
                    Checksummer cs = (Checksummer) iter.next();

                    if (cs.getCheckSum().getValue() != checksum) {
                        throw new IOException(
                            "File collisions with different checksums - " + files);
                    }
                }

                iter = files.iterator();
                iter.next();

                for (; iter.hasNext();) {
                    File file = (File) iter.next();
                    file.delete();
                    logger.info("Deleted colliding and identical file: " +
                        file.getPath());
                    iter.remove();
                }
            }
        } // end sanity checking

        if (isDirectory) {
            length = 0;
        } else {
            length = firstFile.length();
        }

        lastModified = firstFile.lastModified();
    }

    public static void checkSymlink(File file) throws IOException {
        if (!file.getCanonicalPath().equalsIgnoreCase(file.getAbsolutePath())) {
            throw new InvalidDirectoryException("Not following symlink: " +
                file.getAbsolutePath());
        }
    }

    /**
     * @return true if directory contained no files and is now deleted, false
     *                 otherwise.
     * @throws IOException
     */
    private static boolean isEmpty(File dir) throws IOException {
        File[] listfiles = dir.listFiles();

        if (listfiles == null) {
            throw new RuntimeException("Not a directory or IO error: " + dir);
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

        if (!dir.delete()) {
            throw new IOException("Permission denied deleting " +
                dir.getPath());
        }

        return true;
    }

    private void buildFileFiles() throws IOException {
        if (_filefiles != null) {
            return;
        }

        _filefiles = new Hashtable();

        if (!isDirectory()) {
            throw new IllegalArgumentException(
                "listFiles() called on !isDirectory()");
        }

        for (Iterator iter = _roots.iterator(); iter.hasNext();) {
            Root root = (Root) iter.next();
            File file = new File(root.getPath() + "/" + _path);

            if (!file.exists()) {
                continue;
            }

            if (!file.isDirectory()) {
                throw new RuntimeException(file.getPath() +
                    " is not a directory, attempt to getFiles() on it");
            }

            if (!file.canRead()) {
                throw new RuntimeException("Cannot read: " + file);
            }

            File[] tmpFiles = file.listFiles();

            //returns null if not a dir, blah!
            if (tmpFiles == null) {
                throw new NullPointerException("list() on " + file +
                    " returned null");
            }

            for (int i = 0; i < tmpFiles.length; i++) {
                //                try {
                if (tmpFiles[i].isDirectory() && isEmpty(tmpFiles[i])) {
                    continue;
                }

                FileRemoteFile listfile = new FileRemoteFile(_roots,
                        _path + File.separatorChar + tmpFiles[i].getName());
                _filefiles.put(tmpFiles[i].getName(), listfile);

                //                } catch (IOException e) {
                //                    e.printStackTrace();
                //                }
            }
        }

        if (!getName().equals("") && _filefiles.isEmpty()) {
            throw new RuntimeException("Empty (not-root) directory " +
                getPath() + ", shouldn't happen");
        }
    }

    public Collection getFiles() {
        try {
            buildFileFiles();

            return _filefiles.values();
        } catch (IOException e) {
            logger.debug("RuntimeException here", new Throwable());
            throw new RuntimeException(e);
        }
    }

    public String getGroupname() {
        return "drftpd";
    }

    public String getName() {
        return _path.substring(_path.lastIndexOf(File.separatorChar) + 1);
    }

    public String getParent() {
        throw new UnsupportedOperationException();

        //return file.getParent();
    }

    public String getPath() {
        return _path;

        //throw new UnsupportedOperationException();
        //return file.getPath();
    }

    public Collection getSlaves() {
        return new ArrayList();
    }

    public String getUsername() {
        return "drftpd";
    }

    public boolean isDeleted() {
        return false;
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
     * Returns an array of FileRemoteFile:s representing the contents of the
     * directory this FileRemoteFile represents.
     */
    public LightRemoteFileInterface[] listFiles() {
        return (LightRemoteFileInterface[]) getFiles().toArray(new FileRemoteFile[0]);
    }
    public static class InvalidDirectoryException extends IOException {
        /**
         * Constructor for InvalidDirectoryException.
         */
        public InvalidDirectoryException() {
            super();
        }

        /**
         * Constructor for InvalidDirectoryException.
         * @param arg0
         */
        public InvalidDirectoryException(String arg0) {
            super(arg0);
        }
    }
}


class Checksummer extends Thread {
    private static final Logger logger = Logger.getLogger(Checksummer.class);
    private File _f;
    private CRC32 _checkSum;
    private IOException _e;

    public Checksummer(File f) {
    	super("Checksummer - " + f.getPath());
        _f = f;
    }

    /**
     * @return
     */
    public CRC32 getCheckSum() {
        return _checkSum;
    }

    public void run() {
        synchronized (this) {
            _checkSum = new CRC32();

            try {
                CheckedInputStream cis = new CheckedInputStream(new FileInputStream(
                            _f), _checkSum);
                byte[] b = new byte[1024];

                while (cis.read(b) > 0)
                    ;
            } catch (IOException e) {
                logger.warn("", e);
                _e = e;
            }

            notifyAll();
        }
    }
}
