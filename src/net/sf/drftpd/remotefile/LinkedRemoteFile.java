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

import net.sf.drftpd.FatalException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.ID3Tag;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.slave.TransferStatus;
import net.sf.drftpd.util.ListUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import java.rmi.RemoteException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.StringTokenizer;


/**
 * Represents the file attributes of a remote file.
 *
 * @author mog
 * @version $Id: LinkedRemoteFile.java,v 1.164 2004/08/09 20:54:51 teflon114 Exp $
 */
public class LinkedRemoteFile implements Serializable, Comparable,
    LinkedRemoteFileInterface {
    private static final Logger logger = Logger.getLogger(LinkedRemoteFile.class.getName());
    static final long serialVersionUID = 3585958839961835107L;
    private long _checkSum;
    private CaseInsensitiveHashtable _files;
    private transient FtpConfig _ftpConfig;
    private String _group;

    //private Random rand = new Random();
    //private String path;
    private long _lastModified;
    private long _length;
    private String _link;
    private String _name;
    private String _owner;
    private LinkedRemoteFile _parent;

    /////////////////////// SLAVES
    protected List _slaves;
    private long _xfertime = 0;
    protected SFVFile sfvFile;
    protected ID3Tag mp3tag;

    /**
     * Creates an empty RemoteFile directory, usually used as an empty root
     * directory that <link>{merge()} </link> can be called on.
     *
     * Used if no file database exists to start a tree from scratch.
     */
    public LinkedRemoteFile(FtpConfig ftpConfig) {
        _ftpConfig = ftpConfig;

        _lastModified = System.currentTimeMillis();
        _length = 0;
        _parent = null;
        _name = "";
        _files = new CaseInsensitiveHashtable();
        _slaves = null;
    }

    /**
     * Creates a LinkedRemoteFile from file or creates a directory tree
     * representation.
     *
     * Used by DirectoryRemoteFile. Called by other constructor,
     * ConnectionManager is null if called from SlaveImpl.
     *
     * They all end up here.
     *
     * @param parent
     *            the parent of this file
     * @param file
     *            file that this RemoteFile object should represent.
     */
    private LinkedRemoteFile(LinkedRemoteFile parent, RemoteFileInterface file,
        FtpConfig cfg) {
        this(parent, file, file.getName(), cfg);
    }

    private LinkedRemoteFile(LinkedRemoteFile parent, RemoteFileInterface file,
        String name, FtpConfig cfg) {
        if (!ListUtils.isLegalFileName(name)) {
            throw new IllegalArgumentException("Illegal filename");
        }

        if (!file.isFile() && !file.isDirectory()) {
            throw new IllegalArgumentException(
                "File is not a file nor a directory: " + file);
        }

        if (_length == -1) {
            throw new IllegalArgumentException("length() == -1 for " + file);
        }

        _ftpConfig = cfg;
        _lastModified = file.lastModified();
        setOwner(file.getUsername());
        setGroup(file.getGroupname());
        _checkSum = file.getCheckSumCached();
        _parent = parent;

        if (file.isLink()) {
            _link = file.getLinkPath();
        }

        if (parent == null) {
            _name = "";
        } else {
            _name = name.toString();
        }

        if (file.isFile()) {
            _length = file.length();
			_xfertime = file.getXfertime();
            _slaves = Collections.synchronizedList(new ArrayList(
                        file.getSlaves()));
			
            try {
                getParentFile().addSize(length());
            } catch (FileNotFoundException ok) {
                //thrown if this is the root dir
            }
        } else if (file.isDirectory()) {
            //RemoteFileInterface dir[] = file.listFiles();
            //			if (name != "" && dir.length == 0)
            //				throw new FatalException(
            //					"Constructor called with empty dir: " + file);
            _files = new CaseInsensitiveHashtable(file.getFiles().size());

            Stack dirstack = new Stack();

            //for (int i = 0; i < dir.length; i++) {
            for (Iterator iter = file.getFiles().iterator(); iter.hasNext();) {
                RemoteFileInterface file2 = (RemoteFileInterface) iter.next();

                //RemoteFileInterface file2 = dir[i];
                if (file2.isDirectory()) {
                    dirstack.push(file2);

                    continue;
                }

                //the constructor takes care of addSize()
                _files.put(file2.getName(),
                    new LinkedRemoteFile(this, file2, _ftpConfig));
            }

            Iterator i = dirstack.iterator();

            while (i.hasNext()) {
                RemoteFileInterface file2 = (RemoteFileInterface) i.next();
                String filename = file2.getName();

                //the constructor takes care of addSize()
                _files.put(filename,
                    new LinkedRemoteFile(this, file2, _ftpConfig));
            }
        } else {
            throw new RuntimeException();
        }

        //parent == null if creating root dir
    }

    /**
     * Creates a root directory (parent == null) that FileRemoteFile or
     * JDOMRemoteFile is merged on.
     *
     * Also called with null ConnectionManager from slave
     */
    public LinkedRemoteFile(RemoteFileInterface file, FtpConfig cfg)
        throws IOException {
        this(null, file, cfg);
    }

    public static void recursiveRenameLoop(LinkedRemoteFile fromDir,
        LinkedRemoteFile toDir) {
        logger.debug("recursiveRenameLoop(" + fromDir.getPath() + ", " +
            toDir.getPath() + ")");

        for (Iterator iter = new ArrayList(fromDir.getMap().values()).iterator();
                iter.hasNext();) {
            LinkedRemoteFile fromFile = (LinkedRemoteFile) iter.next();

            LinkedRemoteFile toFile;

            try {
                toFile = (LinkedRemoteFile) toDir.getFile(fromFile.getName());
            } catch (QueuedDeletionException e) {
                fromFile.delete();

                continue;
            } catch (FileNotFoundException e) {
                toFile = toDir.putFile(fromFile);
            }

            if (fromFile.isDirectory()) {
                recursiveRenameLoop(fromFile, toFile);
            } else {
                recursiveRenameLoopFile(fromFile, toFile);
            }
        }

        if (fromDir.isEmpty()) {
            fromDir.delete();
        }
    }

    /**
     * @deprecated fromFile
     */
    public static void recursiveRenameLoopFile(LinkedRemoteFile fromFile,
        LinkedRemoteFile toFile) {
        logger.debug("recursiveRenameLoopFile(" + fromFile.getPath() + ", " +
            toFile.getPath());

        Iterator iterator = new ArrayList(fromFile.getSlaves()).iterator();

        while (iterator.hasNext()) {
            RemoteSlave rslave = (RemoteSlave) iterator.next();

            if (rslave.isAvailable()) {
                toFile.addSlave(rslave);
                fromFile.removeSlave(rslave);
            } else {
                logger.debug(rslave + " is offline");

                //fromFile.queueRename(toFile);
            }
        }
    }

    /**
     * @deprecated
     */
    private static void recursiveSetRSlaveAndConfig(LinkedRemoteFile dir,
        FtpConfig ftpConfig, RemoteSlave rslave) {
        dir._ftpConfig = ftpConfig;

        if (dir.isFile()) {
            dir.addSlave(rslave);
        }

        if (dir.isDirectory()) {
            for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
                recursiveSetRSlaveAndConfig((LinkedRemoteFile) iter.next(),
                    ftpConfig, rslave);
            }
        }
    }

    /**
     * Updates lastMofidied() on this directory, use putFile() to avoid it.
     */
    public LinkedRemoteFile addFile(AbstractRemoteFile file) {
        _lastModified = System.currentTimeMillis();

        return putFile(file);
    }

    protected synchronized void addSize(long size) {
        _length += size;

        //		logger.debug(
        //			this +" got " + size + " added, now " + _length,
        //			new Throwable());
        try {
            getParentFile().addSize(size);
        } catch (FileNotFoundException done) {
        }
    }

    public void addSlave(RemoteSlave slave) {
        if (_slaves == null) { //isDirectory()
            throw new IllegalStateException("Cannot addSlave() on a directory");
        }

        if (slave == null) {
            throw new NullPointerException();
        }

        // we get lots of duplicate adds when merging and the slave is already
        // in the file database
        if (_slaves.contains(slave)) {
            return;
        }

        _slaves.add(slave);
    }

    /**
     * @throws ClassCastException
     *             if object is not an instance of RemoteFileInterface.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(Object o) {
        return getName().compareTo(((RemoteFileInterface) o).getName());
    }

    public LinkedRemoteFile createDirectories(String path) {
        NonExistingFile nef = lookupNonExistingFile(path);

        if (nef.exists()) {
            throw new RuntimeException(
                "createDirectories called on already existing directory");
        }

        LinkedRemoteFile dir = nef.getFile();
        StringTokenizer st = new StringTokenizer(nef.getPath(), "/");

        while (st.hasMoreTokens()) {
            try {
                dir.createDirectory(st.nextToken());
            } catch (FileExistsException e) {
                throw new RuntimeException(e);
            }
        }

        return dir;
    }

    public LinkedRemoteFile createDirectory(String fileName)
        throws FileExistsException {
        return createDirectory(null, null, fileName);
    }

    public LinkedRemoteFile createDirectory(String owner, String group,
        String fileName) throws FileExistsException {
        //		LinkedRemoteFile existingfile = (LinkedRemoteFile)
        // _files.get(fileName);
        //		//throws NullPointerException on non-existing directories
        //		if (existingfile.isDeleted())
        //			existingfile.delete();
        //		existingfile = (LinkedRemoteFile) _files.get(fileName);
        if (hasFile(fileName)) {
            throw new FileExistsException(fileName +
                " already exists in this directory");
        }

        LinkedRemoteFile file = addFile(new StaticRemoteFile(null, fileName,
                    owner, group, 0L, System.currentTimeMillis()));
        logger.info("Created directory " + file);
        _lastModified = System.currentTimeMillis();

        return file;
    }

    /**
     * Deletes a file or directory, RemoteSlave handles issues with slaves being offline and queued deletes
     *
     * Trying to lookupFile() or getFile() a deleted file throws
     * FileNotFoundException.
     */
    public void delete() {
        logger.debug("delete(" + getPath() + ")");
        _link = null;

        if (isDirectory()) {
            // need to use a copy of getFiles() for recursive delete to avoid ConcurrentModificationErrors
            for (Iterator iter = new ArrayList(getFiles()).iterator();
                    iter.hasNext();) {
                LinkedRemoteFileInterface myFile = (LinkedRemoteFileInterface) iter.next();
                myFile.delete();
            }

            try {
                if (dirSize() == 0) { //remove empty dir

                    Object ret = getParentFile().getMap().remove(getName());

                    // size SHOULD be 0, but if it isn't, this will even out
                    // the unsynched dirsize
                    getParentFile().addSize(-length());

                    if (ret == null) {
                        throw new NullPointerException();
                    }
                }
            } catch (FileNotFoundException ex) {
                logger.log(Level.FATAL,
                    "FileNotFoundException on getParentFile()", ex);
            }

            return;
        } else {
            synchronized (_slaves) {
                for (Iterator iter = _slaves.iterator(); iter.hasNext();) {
                    RemoteSlave rslave = (RemoteSlave) iter.next();
                    logger.info("DELETE: " + rslave.getName() + ": " +
                        getPath());
                    rslave.deleteFile(getPath());
                    iter.remove();
                }
            }

            if (!_slaves.isEmpty()) {
                throw new RuntimeException(
                    "not all slaves were removed on delete()");
            }

            try {
                if (getParentFile().getMap().remove(getName()) == null) {
                    logger.debug("Unable to remove " + getPath() +
                        " from parent dir, already deleted?");
                }

                getParentFileNull().addSize(-length());
            } catch (FileNotFoundException ex) {
                logger.log(Level.FATAL,
                    "FileNotFoundException on getParentFile()", ex);
            }
        }
    }

    /*
     * This method should only be called by Archive and ArchiveType's as it has no usage anywhere else
     */
    public void deleteOthers(Set destSlaves) {
        if (destSlaves.containsAll(_slaves)) {
            // Do not want to remove the file completely, just delete extraneous slaves
            // this is a safety catch
            return;
        }

        synchronized (_slaves) {
            for (Iterator iter = _slaves.iterator(); iter.hasNext();) {
                RemoteSlave tempSlave = (RemoteSlave) iter.next();

                if (destSlaves.contains(tempSlave)) {
                    continue; // do not want to delete the archived file
                }

                // delete files off of slaves not in destSlaves
                tempSlave.deleteFile(getPath());
                iter.remove();
            }
        }
    }

    public long dirSize() {
        if (_files == null) {
            throw new IllegalStateException(
                "Cannot be called on a non-directory");
        }

        return _files.size();
    }

    public boolean equals(Object obj) {
        if (obj instanceof LinkedRemoteFileInterface &&
                ((LinkedRemoteFileInterface) obj).getPath().equals(getPath())) {
            return true;
        }

        return false;
    }

    public Collection getAvailableSlaves() throws NoAvailableSlaveException {
        ArrayList availableSlaves = new ArrayList();

        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (!rslave.isAvailable()) {
                continue;
            }

            availableSlaves.add(rslave);
        }

        if (availableSlaves.isEmpty()) {
            throw new NoAvailableSlaveException(getPath() +
                " has 0 slaves online");
        }

        return availableSlaves;
    }

    /**
     * Uses cached checksum if the cached checksum is not 0
     */
    public long getCheckSum() throws NoAvailableSlaveException {
        if ((_checkSum == 0) && (_length != 0)) {
            _checkSum = getCheckSumFromSlave();

            if (_checkSum == 0) {
                throw new NoAvailableSlaveException(
                    "Could not find a slave to check crc of " + getPath());
            }
        }

        return _checkSum;
    }

    /**
     * Returns the cached checksum or 0 if no checksum was cached.
     * <p>
     * Use {getCheckSum()} to automatically calculate checksum if no cached
     * checksum is available.
     */
    public long getCheckSumCached() {
        return _checkSum;
    }

    /**
     * Returns 0 if the checksum cannot be read.
     */
    public long getCheckSumFromSlave() {
        try {
            for (Iterator iter = getAvailableSlaves().iterator();
                    iter.hasNext();) {
                RemoteSlave slave = (RemoteSlave) iter.next();

                try {
                    _checkSum = slave.getSlave().checkSum(getPath());
                } catch (RemoteException e) {
                    continue;
                } catch (IOException e) {
                    continue;
                } catch (SlaveUnavailableException e) {
                    continue;
                }

                return _checkSum;
            }
        } catch (NoAvailableSlaveException e) {
            return 0;
        }

        return 0;
    }

    public Collection getDirectories() {
        Collection temp = getFiles();
        ArrayList retlist = new ArrayList();

        for (Iterator iter = temp.iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                retlist.add(file);
            }
        }

        return retlist;
    }

    /**
     * Returns fileName contained in this directory.
     *
     * @param fileName
     * @throws FileNotFoundException
     *             if fileName doesn't exist in the files Map
     */
    public LinkedRemoteFileInterface getFile(String fileName)
        throws FileNotFoundException {
        LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) _files.get(fileName);

        if (file == null) {
            throw new FileNotFoundException("No such file or directory: " +
                fileName);
        }

        return file;
    }

    /**
     * Returns a Collection of all the LinkedRemoteFile objects in this
     * directory, with all .isDeleted() files removed.
     *
     * <p><b>NOTE:</b>Since DrFTPD 1.2, the collection returned can no longer
     * be directly modified.
     *
     * @return a Collection of all the LinkedRemoteFile objects in this
     *         directory.
     */
    public Collection getFiles() {
        if (_files == null) {
            throw new IllegalStateException("Isn't a directory");
        }

        return getFilesMap().values();
    }

    /**
     * Returns a map for this directory, having String name as key and
     * LinkedRemoteFile file as value, with all .isDeleted() files removed.
     *
     * <p><b>NOTE:</b>Since DrFTPD 1.2, the map returned can no longer
     * be directly modified.
     *
     * @return map for this directory, having String name as key and
     *         LinkedRemoteFile file as value, with all .isDeleted() files
     *         removed.
     */
    private Map getFilesMap() {
        return Collections.unmodifiableMap(_files);
    }

    public String getGroupname() {
        if ((_group == null) || _group.equals("")) {
            return "drftpd";
        }

        return _group;
    }

    public RemoteFileInterface getLink() throws FileNotFoundException {
        return lookupFile(getLinkPath());
    }

    public String getLinkPath() {
        return _link;
    }

    /**
     * Returns the underlying Map for this directory.
     *
     * It is dangerous to modify without knowing what you're doing. Dirsize
     * needs to be taken into account as well as sending approperiate commands
     * to the slaves.
     *
     * @return the underlying Map for this directory.
     */
    public Map getMap() {
        return _files;
    }

    public String getName() {
        return _name;
    }

    public LinkedRemoteFileInterface getOldestFile()
        throws ObjectNotFoundException {
        Iterator iter = getFiles().iterator();

        if (!iter.hasNext()) {
            throw new ObjectNotFoundException();
        }

        LinkedRemoteFile oldestFile = (LinkedRemoteFile) iter.next();

        for (; iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();

            if (oldestFile.lastModified() > file.lastModified()) {
                oldestFile = file;
            }
        }

        return oldestFile;
    }

    public String getParent() throws FileNotFoundException {
        return getParentFile().getPath();
    }

    public LinkedRemoteFile getParentFile() throws FileNotFoundException {
        if (_parent == null) {
            throw new FileNotFoundException("root directory has no parent");
        }

        return _parent;
    }

    public LinkedRemoteFile getParentFileNull() {
        return _parent;
    }

    public String getPath() {
        StringBuffer path = new StringBuffer();
        LinkedRemoteFileInterface parent = this;

        while (true) {
            if (parent.getName().length() == 0) {
                break;
            }

            path.insert(0, "/" + parent.getName());

            try {
                parent = parent.getParentFile();

                // throws FileNotFoundException
            } catch (FileNotFoundException ex) {
                break;
            }
        }

        if (path.length() == 0) {
            return "/";
        }

        return path.toString();
    }

    public LinkedRemoteFile getRoot() {
        LinkedRemoteFile root = this;

        try {
            while (true)
                root = root.getParentFile();
        } catch (FileNotFoundException ex) {
            return root;
        }
    }

    public synchronized SFVFile getSFVFile()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        if (sfvFile == null) {
            while (true) {
                RemoteSlave rslave = _ftpConfig.getGlobalContext()
                                               .getSlaveManager()
                                               .getSlaveSelectionManager()
                                               .getASlaveForMaster(this,
                        _ftpConfig);

                try {
                    sfvFile = rslave.getSlave().getSFVFile(getPath());
                    sfvFile.setCompanion(this);

                    break;
                } catch (RemoteException ex) {
                    rslave.handleRemoteException(ex);
                } catch (SlaveUnavailableException e) {
                    continue;
                }
            }
        }

        if (sfvFile.size() == 0) {
            throw new FileNotFoundException(
                "sfv file contains no checksum entries");
        }

        return sfvFile;
    }

    public synchronized ID3Tag getID3v1Tag()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        if (mp3tag == null) {
            logger.warn("getID3v1Tag() : (file) " + getPath());

            while (true) {
                RemoteSlave rslave = _ftpConfig.getGlobalContext()
                                               .getSlaveManager()
                                               .getSlaveSelectionManager()
                                               .getASlaveForMaster(this,
                        _ftpConfig);

                try {
                    mp3tag = rslave.getSlave().getID3v1Tag(getPath());

                    break;
                } catch (RemoteException ex) {
                    rslave.handleRemoteException(ex);
                } catch (SlaveUnavailableException e) {
                    continue;
                }
            }
        } else {
            logger.warn("getID3v1Tag() : (cached) " + getPath());
        }

        return mp3tag;
    }

    /**
     * returns slaves. returns null if a directory.
     */
    public Collection getSlaves() {
        if (_slaves == null) {
            throw new IllegalStateException("getSlaves() called on a directory");
        }

        return _slaves;
    }

    public String getUsername() {
        if ((_owner == null) || _owner.equals("")) {
            return "nobody";
        }

        return _owner;
    }

    public long getXferspeed() {
		logger.warn("getXferspeed = " + length() + " \\ " + getXfertime() + " = " + length() / getXfertime());
        if (getXfertime() == 0) {
            return 0;
        }

        return (length() / getXfertime());
    }

    /**
     * @return xfertime in milliseconds
     */
    public long getXfertime() {
        return _xfertime;
    }

    /**
     * Returns true if this directory contains a file named filename, this is
     * case sensitive.
     *
     * @param filename
     *            The name of the file
     * @return true if this directory contains a file named filename, this is
     *         case sensitive.
     */
    public boolean hasFile(String filename) {
        logger.debug(getPath() + " contains " + filename + "? " +
            _files.containsKey(filename));

        return _files.containsKey(filename);
    }

    public int hashCode() {
        return getName().hashCode();
    }

    /**
     * Returns true if this file or directory uses slaves that are currently
     * offline.
     *
     * @return true if this file or directory uses slaves that are currently
     *         offline.
     */
    public boolean hasOfflineSlaves() {
        if (isFile()) {
            for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iter.next();

                if (rslave == null) {
                    throw new NullPointerException();
                }

                if (!rslave.isAvailable()) {
                    return true;
                }
            }
        } else if (isDirectory()) {
            for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
                if (((LinkedRemoteFileInterface) iter.next()).hasOfflineSlaves()) {
                    return true;
                }
            }
        }

        return false;
    }

    public boolean hasSlave(RemoteSlave slave) {
        return _slaves.contains(slave);
    }

    /**
     * Does file have online slaves?
     *
     * @return Always true for directories
     */
    public boolean isAvailable() {
        if (isDirectory()) {
            return true;
        }

        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (rslave == null) {
                throw new RuntimeException();
            }

            if (rslave.isAvailable()) {
                return true;
            }
        }

        return false;
    }

    public boolean isDirectory() {
        return !isFile();
    }

    /**
     * @return true if directory is empty.
     */
    private boolean isEmpty() {
        if (_files == null) {
            throw new IllegalStateException();
        }

        return _files.isEmpty();
    }

    public boolean isFile() {
        return (_files == null) && (_slaves != null);
    }

    /**
     * isLink() && isDeleted() means queued rename, target is getLink().
     */
    public boolean isLink() {
        return _link != null;
    }

    public long lastModified() {
        return _lastModified;
    }

    public long length() {
        //		if (isDirectory()) {
        //			long length = 0;
        //			for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
        //				length += ((LinkedRemoteFile) iter.next()).length();
        //			}
        //			if (_length != 0 && length != _length)
        //				logger.warn(
        //					"",
        //					new Throwable(
        //						"Cached checksum missmatch: "
        //							+ length
        //							+ " != "
        //							+ _length
        //							+ " for "
        //							+ toString()));
        //			_length = length;
        //			return length;
        //		}
        if (_length < 0) {
            return 0;
        }

        return _length;
    }

    public LinkedRemoteFile lookupFile(String path)
        throws FileNotFoundException {
        return lookupFile(path, false);
    }

    public LinkedRemoteFile lookupFile(String path, boolean includeDeleted)
        throws FileNotFoundException {
        NonExistingFile ret = lookupNonExistingFile(path);

        if (!ret.exists()) {
            throw new FileNotFoundException(path + ": File not found");
        }

        return (LinkedRemoteFile) ret.getFile();
    }

    public NonExistingFile lookupNonExistingFile(String path) {
        if (path == null) {
            throw new IllegalArgumentException("null path not allowed");
        }

        LinkedRemoteFile currFile = this;

        if (path.charAt(0) == '/') {
            currFile = getRoot();
        }

        //check for leading ~
        if ((path.length() == 1) && path.equals("~")) {
            currFile = getRoot();
            path = "";
        } else if ((path.length() >= 2) && path.substring(0, 2).equals("~/")) {
            currFile = getRoot();
            path = path.substring(2);
        }

        StringTokenizer st = new StringTokenizer(path, "/");

        while (st.hasMoreTokens()) {
            String currFileName = st.nextToken();

            if (currFileName.equals(".")) {
                continue;
            }

            if (currFileName.equals("..")) {
                try {
                    currFile = currFile.getParentFile();
                } catch (FileNotFoundException ex) {
                }

                continue;
            }

            LinkedRemoteFile nextFile;

            try {
                nextFile = (LinkedRemoteFile) currFile.getFile(currFileName);
            } catch (FileNotFoundException ex) {
                StringBuffer remaining = new StringBuffer(currFileName);

                if (st.hasMoreElements()) {
                    remaining.append('/').append(st.nextToken(""));
                }

                return new NonExistingFile(currFile, remaining.toString());
            }

            currFile = nextFile;
        }

        return new NonExistingFile(currFile, null);
    }

    /**
     * Returns path for a non-existing file. Performs path normalization and
     * returns an absolute path
     */
    public String lookupPath(String path) {
        NonExistingFile ret = lookupNonExistingFile(path);

        if (ret.exists()) {
            return ret.getFile().getPath();
        }

        return ret.getFile().getPath() + '/' + ret.getPath();
    }

    public SFVFile lookupSFVFile()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        if (!isDirectory()) {
            throw new IllegalStateException(
                "lookupSFVFile must be called on a directory");
        }

        for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface myFile = (LinkedRemoteFileInterface) iter.next();

            if (myFile.getName().toLowerCase().endsWith(".sfv")) {
                return myFile.getSFVFile();
            }
        }

        throw new FileNotFoundException("no sfv file in directory");
    }

    public String lookupMP3File()
        throws IOException, FileNotFoundException, NoAvailableSlaveException {
        if (!isDirectory()) {
            throw new IllegalStateException(
                "lookupMP3File() must be called on a directory");
        }

        for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface myFile = (LinkedRemoteFileInterface) iter.next();

            if (myFile.getName().toLowerCase().endsWith(".mp3")) {
                return myFile.getPath();
            }
        }

        throw new FileNotFoundException("no mp3 file in directory");
    }

    /**
     * Use addFile() if you want lastModified to be updated.
     */
    public LinkedRemoteFile putFile(RemoteFileInterface file) {
        return putFile(file, file.getName());
    }

    /**
     * @param torslave
     *            RemoteSlave to replicate to.
     */

    //	public void replicate(final RemoteSlave torslave)
    //		throws NoAvailableSlaveException, IOException {
    //
    //		final RemoteSlave fromslave = getASlaveForDownload();
    //		Transfer fromtransfer = fromslave.getSlave().listen(false);
    //		final Transfer totransfer =
    //			torslave.getSlave().connect(
    //				new InetSocketAddress(
    //					fromslave.getInetAddress(),
    //					fromtransfer.getLocalPort()),
    //				false);
    //
    //		Thread t = new Thread(new Runnable() {
    //			public void run() {
    //				try {
    //					totransfer.receiveFile(
    //						getParentFile().getPath(),
    //						'I',
    //						getName(),
    //						0L);
    //				} catch (RemoteException e) {
    //					torslave.handleRemoteException(e);
    //					logger.warn(EMPTY_STRING, e);
    //				} catch (FileNotFoundException e) {
    //					throw new FatalException(e);
    //				} catch (IOException e) {
    //					throw new RuntimeException(e);
    //				}
    //			}
    //		});
    //		t.start();
    //		fromtransfer.sendFile(getPath(), 'I', 0, false);
    //	}

    /**
     * @param toName
     *            name argument to LinkedRemoteFile constructor
     */
    private LinkedRemoteFile putFile(RemoteFileInterface file, String toName) {
        if (_files.containsKey(toName)) {
            throw new IllegalStateException(getPath() + "/" + toName +
                " already exists.");
        }

        //validate
        if (file.isFile()) {
            if (file.getSlaves() == null) {
                throw new RuntimeException(file.toString());
            }

            for (Iterator iter = file.getSlaves().iterator(); iter.hasNext();) {
                if (iter.next() == null) {
                    throw new RuntimeException();
                }
            }
        }

        //the constructor takes care of addSize()
        LinkedRemoteFile linkedfile = new LinkedRemoteFile(this, file, toName,
                _ftpConfig);
        _files.put(linkedfile.getName(), linkedfile);

        return linkedfile;
    }

    public TransferStatus receiveFile(Transfer transfer, char type, long offset)
        throws IOException {
        return transfer.receiveFile(getParent(), type, getName(), offset);
    }

    /**
     * Merges mergedir directory onto <code>this</code> directories. If
     * duplicates exist, the slaves are added to this object and the
     * file-attributes of the oldest file (lastModified) are kept.
     */
    public void remerge(LinkedRemoteFile mergedir, RemoteSlave rslave)
        throws IOException {
        if (rslave == null) {
            throw new IllegalArgumentException("slave cannot be null");
        }

        if (mergedir == null) {
            throw new IllegalArgumentException("mergedir cannot be null");
        }

        if (!isDirectory()) {
            throw new IllegalArgumentException(
                "merge() called on a non-directory");
        }

        if (!mergedir.isDirectory()) {
            throw new IllegalArgumentException("argument is not a directory");
        }

        synchronized (_files) {
            // remove all slaves not in mergedir.getFiles()
            // unmerge() gets called on all files not on slave & all directories
            for (Iterator i = Collections.unmodifiableCollection(
                        new ArrayList(_files.values())).iterator();
                    i.hasNext();) {
                LinkedRemoteFile file = (LinkedRemoteFile) i.next();

                synchronized (file) {
                    if (mergedir.hasFile(file.getName())) {
                        LinkedRemoteFile mergefile = (LinkedRemoteFile) mergedir.getFile(file.getName());

                        if (file.isFile()) {
                            if (file.length() == mergefile.length()) {
                                file.addSlave(rslave);
                            } else {
                                //// conflict ////
                                if (mergefile.length() == 0) {
                                    logger.log(Level.INFO,
                                        "Deleting conflicting 0byte " +
                                        mergefile + " on " + rslave);
                                    rslave.deleteFile(mergefile.getPath());
                                    file.unmergeFile(rslave);
                                } else if ((file.getSlaves().size() == 1) &&
                                        (file.length() == 0)) {
                                    logger.info("Deleting conflicting 0byte " +
                                        file + " on " + rslave);

                                    RemoteSlave rslave2 = (RemoteSlave) file.getSlaves()
                                                                            .iterator()
                                                                            .next();
                                    rslave2.deleteFile(file.getPath());
                                } else {
                                    if (((file.getSlaves().size() == 1) &&
                                            file.hasSlave(rslave))) {
                                        //we're the only slave with the file.
                                        file.setLength(mergefile.length());
                                        file.setCheckSum(0L);
                                        file.setLastModified(mergefile.lastModified());
                                    } else {
                                        //// conflict, rename file... ////
                                        rslave.rename(getPath() + "/" +
                                            mergefile.getName(), getPath(),
                                            mergefile.getName() + "." +
                                            rslave.getName() + ".conflict");
                                        mergefile._name = mergefile._name +
                                            "." + rslave.getName() +
                                            ".conflict";
                                        mergefile.addSlave(rslave);
                                        _files.put(mergefile.getName(),
                                            mergefile);
                                        logger.log(Level.WARN,
                                            "2 or more slaves contained same file with different sizes, renamed to " +
                                            mergefile.getName());
                                        file.unmergeFile(rslave);
                                    }
                                }
                            }
                        } else {
                            file.remerge(mergefile, rslave);
                        }

                        // this is done so later new files can be merged without overhead of looking through already merged files
                        mergedir.getMap().remove(mergefile.getName());
                    } else { // doesn't have file

                        if (file.isDirectory()) {
                            file.unmergeDir(rslave);
                        } else {
                            file.unmergeFile(rslave);
                        }
                    }
                }
            }

            for (Iterator iter = mergedir.getFiles().iterator();
                    iter.hasNext();) {
                LinkedRemoteFile newfile = (LinkedRemoteFile) iter.next();

                try {
                    getFile(newfile.getName());

                    continue; // local file exists
                } catch (FileNotFoundException good) {
                }

                recursiveSetRSlaveAndConfig(newfile, _ftpConfig, rslave);
                newfile._parent = this;

                // already in _files?
                _files.put(newfile.getName(), newfile);
                logger.info(newfile.getPath() + " added from " +
                    rslave.getName());
            }
        }
    }

    /*        private void remergePass1(LinkedRemoteFile mergedir, RemoteSlave rslave) {
                    if (!isDirectory())
                            throw new IllegalArgumentException("merge() called on a non-directory");

                    if (!mergedir.isDirectory())
                            throw new IllegalArgumentException("argument is not a directory");

                    // add/merge all files from mergedir
                    for (Iterator i = mergedir.getFiles().iterator(); i.hasNext();) {
                            LinkedRemoteFile slavefile = (LinkedRemoteFile) i.next();
                            if (slavefile.isDirectory() && slavefile.length() == 0) {
                                    logger.fatal(
                                            "Attempt to add empty directory: "
                                                    + slavefile
                                                    + " from "
                                                    + rslave.getName());
                                    continue;
                            }
                            //this is localdir
                            //localfile is local file
                            //mergedir is mergedir argument to remerge()
                            //mergefile is file to be merged
                            LinkedRemoteFile localfile =
                                    (LinkedRemoteFile) _files.get(slavefile.getName());
                            // two scenarios: localfile does/doesn't exist.
                            if (localfile == null) {
                                    // local file does not exist, just put it in the hashtable
                                    recursiveSetRSlaveAndConfig(slavefile, _ftpConfig, rslave);
                                    slavefile._parent = this;
                                    _files.put(slavefile.getName(), slavefile);
                                    logger.info(
                                            slavefile.getPath() + " added from " + rslave.getName());
                            } else { //file exists
                                    if (localfile.isDeleted()) {
                                            //logger.debug("localfile isDeleted() " + localfile);
                                            //// queued delete or rename ////
                                            if (localfile.isLink()) {
                                                    String linktarget = localfile.getLinkPath();
                                                    //// rename ////
                                                    try {
                                                            LinkedRemoteFileInterface renameTo =
                                                                    (LinkedRemoteFileInterface) localfile.getLink();
                                                            logger.debug(
                                                                    "queued rename for "
                                                                            + localfile.getPath()
                                                                            + " to "
                                                                            + renameTo.getPath());
                                                            try {
                                                                    rslave.getSlave().rename(
                                                                            localfile.getPath(),
                                                                            renameTo.getParent(),
                                                                            renameTo.getName());
                                                                    //recusive migration of rslaves from source to
                                                                    // dest
                                                                    if (localfile.isFile()) {
                                                                            recursiveRenameLoopFile(
                                                                                    localfile,
                                                                                    (LinkedRemoteFile) renameTo);
                                                                    } else {
                                                                            recursiveRenameLoop(
                                                                                    localfile,
                                                                                    (LinkedRemoteFile) renameTo);
                                                                    }

                                                                    //migrate mergefile on mergedir or files will
                                                                    // be removed cause they aren't in slave
                                                                    // filelist
                                                                    //simple ugly move of the lrf object so that
                                                                    // remergePass2() won't delete it
                                                                    {
                                                                            NonExistingFile ret =
                                                                                    slavefile.lookupNonExistingFile(
                                                                                            linktarget);
                                                                            LinkedRemoteFile destDir = ret.getFile();
                                                                            if (!ret.hasPath())
                                                                                    throw new RuntimeException(
                                                                                            ret
                                                                                                    + " - target already exists on the slave - ???");
                                                                            StringTokenizer destst =
                                                                                    new StringTokenizer(ret.getPath(), "/");
                                                                            String desttok = null;
                                                                            if (!destst.hasMoreTokens())
                                                                                    throw new RuntimeException("Invalid queued rename target");
                                                                            while (destst.hasMoreTokens()) {
                                                                                    desttok = destst.nextToken();
                                                                                    if (destst.hasMoreTokens()) {
                                                                                            destDir =
                                                                                                    destDir.createDirectory(
                                                                                                            "drftpd",
                                                                                                            "drftpd",
                                                                                                            desttok);
                                                                                            logger.debug(
                                                                                                    "Created " + destDir.getPath());
                                                                                    }
                                                                            }
                                                                            mergedir._files.remove(slavefile.getName());
                                                                            slavefile._name = desttok;
                                                                            destDir.putFile(slavefile);
                                                                            slavefile._parent = destDir;
                                                                            logger.debug(
                                                                                    "Renamed "
                                                                                            + slavefile.getName()
                                                                                            + " to "
                                                                                            + destDir.getPath());
                                                                            //remove source
                                                                    }

                                                                    //file.removeSlave(rslave);
                                                                    //renameTo.addSlave(rslave);
                                                            } catch (RemoteException e) {
                                                                    rslave.handleRemoteException(e);
                                                            } catch (SlaveUnavailableException e) {
                                                                    throw new RuntimeException(e);
                                                            } catch (IOException e) {
                                                                    logger.warn("", e);
                                                            }
                                                            continue;
                                                    } catch (FileNotFoundException e) {
                                                            //localfile.getLink() failed
                                                            //target doesn't exist, queued delete instead
                                                            logger.info(
                                                                    localfile.getPath()
                                                                            + " target didn't exist for queued rename, scheduling for deletion");
                                                            localfile._link = null;
                                                    }
                                            }

                                            //// delete ////
                                            if (!localfile.isLink()) {
                                                    logger.log(
                                                            Level.WARN,
                                                            "Queued delete on "
                                                                    + rslave
                                                                    + " for file "
                                                                    + slavefile);
                                                    if (!slavefile.isDirectory())
                                                            slavefile.addSlave(rslave);
                                                    slavefile.delete();
                                                    localfile.delete();
                                                    continue;
                                                    //TODO subdir contains queued for rename files.
                                            }
                                    } // end queued del/ren

                                    if (slavefile.isFile()
                                            && localfile.length() != slavefile.length()) {
                                            //// conflict ////
                                            Collection filerslaves = slavefile.getSlaves();

                                            if ((filerslaves.size() == 1
                                                    && filerslaves.contains(rslave))
                                                    || localfile.length() == 0) {
                                                    //we're the only slave with the file.
                                                    localfile.setLength(slavefile.length());
                                                    localfile.setCheckSum(0L);
                                                    localfile.setLastModified(slavefile.lastModified());
                                            } else if (slavefile.length() == 0) {
                                                    logger.log(
                                                            Level.INFO,
                                                            "Deleting conflicting 0byte "
                                                                    + slavefile
                                                                    + " on "
                                                                    + rslave);
                                                    try {
                                                            rslave.getSlave().delete(slavefile.getPath());
                                                    } catch (PermissionDeniedException ex) {
                                                            logger.log(
                                                                    Level.FATAL,
                                                                    "Error deleting 0byte file on " + rslave,
                                                                    ex);
                                                    } catch (Exception e) {
                                                            throw new FatalException(e);
                                                    }
                                                    continue;
                                            } else {
                                                    //// conflict, rename file... ////
                                                    try {
                                                            rslave.getSlave().rename(
                                                                    getPath() + "/" + slavefile.getName(),
                                                                    getPath(),
                                                                    slavefile.getName()
                                                                            + "."
                                                                            + rslave.getName()
                                                                            + ".conflict");
                                                            slavefile._name =
                                                                    slavefile.getName()
                                                                            + "."
                                                                            + rslave.getName()
                                                                            + ".conflict";
                                                            slavefile.addSlave(rslave);
                                                            _files.put(slavefile.getName(), slavefile);
                                                            logger.log(
                                                                    Level.WARN,
                                                                    "2 or more slaves contained same file with different sizes, renamed to "
                                                                            + slavefile.getName());
                                                            continue;
                                                    } catch (Exception e) {
                                                            throw new FatalException(e);
                                                    }
                                            }
                                    }

                                    // 4 scenarios: new/existing file/directory
                                    if (slavefile.isDirectory()) {
                                            if (!localfile.isDirectory())
                                                    throw new RuntimeException(
                                                            "!!! ERROR: Directory/File conflict: "
                                                                    + slavefile
                                                                    + " and "
                                                                    + localfile
                                                                    + " from "
                                                                    + rslave.getName());
                                            // is a directory -- dive into directory and start merging
                                            localfile.remergePass1(slavefile, rslave);
                                    } else {
                                            if (!slavefile.isFile())
                                                    throw new RuntimeException();
                                            if (localfile.isDirectory())
                                                    throw new RuntimeException(
                                                            "!!! ERROR: File/Directory conflict: "
                                                                    + slavefile
                                                                    + " and "
                                                                    + localfile
                                                                    + " from "
                                                                    + rslave.getName());
                                            localfile.addSlave(rslave);
                                    }
                            } // file != null
                    }

            }
    */
    /*        private void remergePass2(LinkedRemoteFile mergedir, RemoteSlave rslave) {
                    synchronized (_files) {
                            // remove all slaves not in mergedir.getFiles()
                            // unmerge() gets called on all files not on slave & all directories
                            //for (Iterator i = new ArrayList(getFilesMap().values()).iterator();
                            // getFilesMap() returns a copy of the list and without isDeleted files
                            for (Iterator i = new ArrayList(_files.values()).iterator();
                                    i.hasNext();
                                    ) {
                                    LinkedRemoteFile file = (LinkedRemoteFile) i.next();
                                    if (mergedir == null) { // slave doesn't have the directory
                                            if (file.isFile()) {
                                                    file.unmergeFile(rslave);
                                            } else {
                                                    file.remergePass2(null, rslave);
                                            }
                                            continue;
                                    }
                                    if (!mergedir.hasFile(file.getName())) {
                                            if (file.isFile()) {
                                                    file.unmergeFile(rslave);
                                            } else {
                                                    file.remergePass2(null, rslave);
                                            }
                                    } else {
                                            if (file.isDirectory()) {
                                                    try {
                                                            file.remergePass2(
                                                                    (LinkedRemoteFile) mergedir.getFile(
                                                                            file.getName()),
                                                                    rslave);
                                                    } catch (FileNotFoundException e) {
                                                            throw new RuntimeException(
                                                                    "inconsistent with hasFile() above",
                                                                    e);
                                                    }
                                            }
                                            // else slave has the file
                                    }
                            }
                    }
            }*/

    /**
     * Shorthand for _slaves.remove() that checks if _slaves is empty and calls .delete() if it is.
     */
    public boolean removeSlave(RemoteSlave slave) {
        if (_slaves == null) {
            throw new IllegalStateException("Cannot removeSlave() on directory");
        }

        boolean ret = _slaves.remove(slave);

        if (_slaves.isEmpty()) {
            delete();
        }

        return ret;
    }

    /**
     * Renames this file
     */
    public LinkedRemoteFile renameTo(String toDirPath, String toName)
        throws IOException, FileNotFoundException {
        if (toDirPath.charAt(0) != '/') {
            throw new RuntimeException(
                "renameTo() must be given an absolute path as argument");
        }

        if (toName.indexOf('/') != -1) {
            throw new RuntimeException(
                "Cannot rename to non-existing directory");
        }

        if (_ftpConfig == null) {
            throw new RuntimeException("_ftpConfig is null: " + this);
        }

        LinkedRemoteFile toDir = lookupFile(toDirPath);
        // throws FileNotFoundException
        {
            LinkedRemoteFile tmpDir = toDir;

            do {
                if (tmpDir == this) {
                    throw new IOException(
                        "Cannot rename into a subdirectory of self");
                }
            } while ((tmpDir = tmpDir.getParentFileNull()) != null);
        }

        //slaves are copied here too...
        LinkedRemoteFile toFile = toDir.putFile(this, toName);

        if (isDirectory()) {
            for (Iterator iter = _ftpConfig.getGlobalContext().getSlaveManager()
                                           .getSlaves().iterator();
                    iter.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iter.next();

                try {
                    rslave.rename(getPath(), toDirPath, toName);
                } catch (IOException ex) {
                    logger.log(Level.FATAL,
                        "IOException in renameTo() for dir for " +
                        rslave.getName(), ex);
                }
            }

            // need to remove the now moved directory
            delete();
        } else { // isFile()
            toFile._slaves = Collections.synchronizedList(new ArrayList());

            for (Iterator iter = new ArrayList(getSlaves()).iterator();
                    iter.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iter.next();

                try {
                    rslave.rename(getPath(), toDirPath, toName);
                } catch (IOException ex) {
                    logger.log(Level.FATAL,
                        "IO error from " + rslave.getName() +
                        " on a file in LinkedRemoteFile", ex);
                }

                // slave is now offline, but when it comes back online, file will be in correct place
                removeSlave(rslave);
                toFile.addSlave(rslave);
            }
        }

        return toFile;
    }

    public TransferStatus sendFile(Transfer transfer, char type, long offset)
        throws IOException {
        return transfer.sendFile(getPath(), type, offset);
    }

    public void setCheckSum(long checkSum) {
        _checkSum = checkSum;
    }

    public void setGroup(String group) {
        _group = (group != null) ? group.intern() : null;
    }

    public void setLastModified(long lastModified) {
        _lastModified = lastModified;
    }

    public void setLength(long length) {
        getParentFileNull().addSize(length - _length);
        _length = length;
    }

    public void setOwner(String owner) {
        _owner = (owner != null) ? owner.intern() : null;
    }

    private void setRSlaveAndConfig(LinkedRemoteFile dir, FtpConfig cfg,
        RemoteSlave rslave) {
        if (dir.isFile()) {
            dir._ftpConfig = cfg;
            dir.addSlave(rslave);

            return;
        }

        for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
            file._ftpConfig = cfg;

            if (file.isDirectory()) {
                setRSlaveAndConfig(file, cfg, rslave);
            } else {
                file.addSlave(rslave);
            }
        }
    }

    public void setXfertime(long xfertime) {
        _xfertime = xfertime;
    }

    public String toString() {
        StringBuffer ret = new StringBuffer();
        ret.append("LinkedRemoteFile[\"" + this.getName() + "\",");

        if (isFile()) {
            ret.append("xfertime:" + _xfertime + ",");
        }

        if (isLink()) {
            ret.append("link:" + getLinkPath() + ",");
        }

        //ret.append(slaves);
        if (_slaves != null) {
            Iterator i = _slaves.iterator();

            //			Enumeration e = slaves.elements();
            ret.append("slaves:[");

            // HACK: How can one find out the endpoint without using regexps?
            //Pattern p = Pattern.compile("endpoint:\\[(.*?):.*?\\]");
            while (i.hasNext()) {
                RemoteSlave rslave = (RemoteSlave) i.next();

                if (rslave == null) {
                    throw new FatalException("There's a null in rslaves");
                }

                ret.append(rslave.getName());

                if (!rslave.isAvailable()) {
                    ret.append("-OFFLINE");
                }

                if (i.hasNext()) {
                    ret.append(",");
                }
            }

            ret.append("]");
        }

        if (isDirectory()) {
            ret.append("[directory(" + _files.size() + ")]");
        }

        ret.append("]");

        return ret.toString();
    }

    public synchronized void unmergeDir(RemoteSlave rslave) {
        if (!isDirectory()) {
            throw new IllegalStateException();
        }

        for (Iterator i = new ArrayList(_files.values()).iterator();
                i.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) i.next();

            if (file.isDirectory()) {
                file.unmergeDir(rslave);

                //remove empty deleted directories
                if (file.dirSize() == 0) {
                    file.delete();
                }
            } else {
                file.unmergeFile(rslave);
            }
        }
    }

    public void unmergeFile(RemoteSlave rslave) {
        if (!isFile()) {
            throw new IllegalStateException();
        }

        if (removeSlave(rslave)) {
            logger.warn(getPath() + " deleted from " + rslave.getName());
        }

        //it's safe to remove it as it has no slaves.
        // removeSlave() takes care of this
        //		if (file.getSlaves().size() == 0) {
        //			i.remove();
        //			getParentFileNull().addSize(-file.length());
        //		}
    }

    public boolean isDeleted() {
        if (isDirectory()) {
            return false;
        }

        if (_slaves.isEmpty()) {
            return true;
        }

        return false;
    }

    public static class CaseInsensitiveHashtable extends Hashtable {
        public CaseInsensitiveHashtable() {
            super();
        }

        public CaseInsensitiveHashtable(int initialCapacity) {
            super(initialCapacity);
        }

        public CaseInsensitiveHashtable(int initialCapacity, float loadFactor) {
            super(initialCapacity, loadFactor);
        }

        public CaseInsensitiveHashtable(Map t) {
            super(t);
        }

        public synchronized boolean containsKey(Object key) {
            return super.containsKey(((String) key).toLowerCase());
        }

        public synchronized Object get(Object key) {
            return super.get(((String) key).toLowerCase());
        }

        public synchronized Object put(Object key, Object value) {
            return super.put(((String) key).toLowerCase(), value);
        }

        public synchronized Object remove(Object key) {
            return super.remove(((String) key).toLowerCase());
        }
    }

    public static class NonExistingFile {
        private LinkedRemoteFile _file;
        private String _path;

        public NonExistingFile(LinkedRemoteFile file, String path) {
            _file = file;
            _path = path;
        }

        /**
         * Return true if getPath() returns a null value, i.e. <!-- --> returns
         * true if the file exists.
         */
        public boolean exists() {
            return _path == null;
        }

        public LinkedRemoteFile getFile() {
            return _file;
        }

        public String getPath() {
            return _path;
        }

        public String toString() {
            return "[NonExistingFile:file=" + getFile().getPath() + ",path=" +
            getPath() + "]";
        }
    }
}
