/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.vfs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.DynamicConfigHelper;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.dynamicdata.element.ConfigElement;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.drftpd.common.dynamicdata.DynamicConfigHelper.configHelper;


/**
 * VirtualFileSystemInode is an abstract class used to handle basic functions
 * of files/dirs/links and to keep an hierarchy/organization of the FS.
 */
@JsonTypeInfo(use = JsonTypeInfo.Id.CLASS, property = "@type")
public abstract class VirtualFileSystemInode implements Commitable {

    protected static final Logger logger = LogManager.getLogger(VirtualFileSystemInode.class);
    @JsonIgnore
    protected transient String _name;
    @JsonIgnore
    protected transient VirtualFileSystemDirectory _parent;
    protected String _username;
    protected String _group;
    private Map<Key<?>, ConfigElement<?>> _configs = new HashMap<>();
    private Map<Key<?>, ConfigElement<?>> _plugins = new HashMap<>();
    protected long _lastModified;
    protected long _creationTime;
    @JsonIgnore
    private transient boolean _inodeLoaded;

    public VirtualFileSystemInode() {
        _lastModified = System.currentTimeMillis();
        _creationTime = _lastModified;
    }

    public VirtualFileSystemInode(String user, String group) {
        this();
        _username = user;
        _group = group;
    }

    /**
     * @return the VirtualFileSystem instance.
     */
    public static VirtualFileSystem getVFS() {
        return VirtualFileSystem.getVirtualFileSystem();
    }

    public String descriptiveName() {
        return getPath();
    }

    public void writeToDisk() throws IOException {
        VirtualFileSystem.getVirtualFileSystem().writeInode(this);
    }

    /**
     * Need to ensure that this is called after each (non-transient) change to
     * the Inode.<br>
     * When called, this method will save the Inode data to the disk.
     */
    public void commit() {
        //logger.debug("Committing " + getPath());
        CommitManager.getCommitManager().add(this);
    }

    /**
     * Deletes a file, directory, or link, RemoteSlave handles issues with
     * slaves being offline and queued deletes
     */
    public void delete() {
        logger.info("delete({})", this);

        String path = getPath();
        VirtualFileSystem.getVirtualFileSystem().deleteInode(getPath());
        _parent.removeChild(this);
        CommitManager.getCommitManager().remove(this);

        getVFS().notifyInodeDeleted(this, path);
    }

    /**
     * @return the owner primary group.
     */
    public String getGroup() {
        return _group;
    }

    /**
     * @param group Sets the group which owns the Inode.
     */
    public void setGroup(String group) {
        _group = group;
        if (isInodeLoaded()) {
            commit();
            getVFS().notifyOwnershipChanged(this, getUsername(), _group);
        }
    }

    public Map<Key<?>, ConfigElement<?>> getConfigs() {
        return _configs;
    }

    public void setConfigs(Map<Key<?>, ConfigElement<?>> _configs) {
        this._configs = _configs;
    }

    public Map<Key<?>, ConfigElement<?>> getPlugins() {
        return _plugins;
    }

    public void setPlugins(Map<Key<?>, ConfigElement<?>> _plugins) {
        this._plugins = _plugins;
    }

    public DynamicConfigHelper pluginsHelper() {
        return configHelper(_plugins);
    }

    public DynamicConfigHelper configsHelper() {
        return configHelper(_configs);
    }

    /**
     * @return when the file was last modified.
     */
    public long getLastModified() {
        return _lastModified;
    }

    /**
     * @param modified Set when the file was last modified.
     */
    public void setLastModified(long modified) {
        if (_lastModified != modified) {
            _lastModified = modified;
            if (isInodeLoaded()) {
                commit();
                getVFS().notifyLastModifiedChanged(this, _lastModified);
            }
        }
    }

    /**
     * @return when the file was created.
     */
    public long getCreationTime() {
        return _creationTime;
    }

    /**
     * @param created Set when the file was created.
     */
    public void setCreationTime(long created) {
        if (_creationTime != created) {
            _creationTime = created;
            if (isInodeLoaded()) {
                commit();
            }
        }
    }

    /**
     * @return the file name.
     */
    public String getName() {
        return _name;
    }

    /**
     * Sets the Inode name.
     */
    protected void setName(String name) {
        _name = name;
    }

    /**
     * @return parent dir of the file/directory/link.
     */
    public VirtualFileSystemDirectory getParent() {
        return _parent;
    }

    public void setParent(VirtualFileSystemDirectory directory) {
        _parent = directory;
    }

    /**
     * @return Returns the full path.
     */
    protected String getPath() {
        if (this instanceof VirtualFileSystemRoot) {
            return VirtualFileSystem.separator;
        }
        if (getParent() instanceof VirtualFileSystemRoot) {
            return VirtualFileSystem.separator + getName();
        }
        return getParent().getPath() + VirtualFileSystem.separator + getName();
    }

    /**
     * @return Returns the size of the dir/file/link.
     */
    public abstract long getSize();

    /**
     * Set the size of the dir/file/link.
     */
    public abstract void setSize(long l);

    /**
     * Sets that the inode has been fully loaded from disk
     */
    public void inodeLoadCompleted() {
        _inodeLoaded = true;
    }

    /**
     * Returns whether the inode has been fully loaded from disk
     */
    public boolean isInodeLoaded() {
        return _inodeLoaded;
    }

    /**
     * @return the owner username.
     */
    public String getUsername() {
        return _username;
    }

    /**
     * @param user The user to set.
     */
    public void setUsername(String user) {
        _username = user;
        if (isInodeLoaded()) {
            commit();
            getVFS().notifyOwnershipChanged(this, _username, getGroup());
        }
    }

    public boolean isDirectory() {
        return this instanceof VirtualFileSystemDirectory;
    }

    public boolean isFile() {
        return this instanceof VirtualFileSystemFile;
    }

    public boolean isLink() {
        return this instanceof VirtualFileSystemLink;
    }

    /**
     * Renames this Inode.
     *
     * @param destination
     * @throws FileExistsException
     */
    protected void rename(String destination) throws FileExistsException {
        if (!destination.startsWith(VirtualFileSystem.separator)) {
            throw new IllegalArgumentException(destination + " is a relative path and it should be a full path");
        }

        try {
            VirtualFileSystemInode destinationInode = getVFS().getInodeByPath(destination);
            if (!destinationInode.equals(this)) {
                throw new FileExistsException(destination + "already exists");
            }
        } catch (FileNotFoundException e) {
            // This is good
        }

        VirtualFileSystemDirectory destinationDir = null;
        try {
            destinationDir = (VirtualFileSystemDirectory) getVFS().getInodeByPath(VirtualFileSystem.stripLast(destination));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Error in logic, this should not happen", e);
        }
        // Ensure source/destination is flushed to ondisk VFS in case either is newly created
        CommitManager.getCommitManager().flushImmediate(destinationDir);
        CommitManager.getCommitManager().flushImmediate(this);
        String fileString = "rename(" + this + ")";
        String sourcePath = getPath();
        _parent.removeChild(this);
        try {
            VirtualFileSystem.getVirtualFileSystem().renameInode(
                    this.getPath(),
                    destinationDir.getPath() + VirtualFileSystem.separator
                            + VirtualFileSystem.getLast(destination));
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Tried to rename a file that does not exist: " + getPath(), e);
        } catch (PermissionDeniedException e) {
            throw new RuntimeException("FileSystemError", e);
        }
        _name = VirtualFileSystem.getLast(destination);
        _parent = destinationDir;
        _parent.addChild(this, true);
        fileString = fileString + ",(" + this + ")";
        logger.info(fileString);
        getVFS().notifyInodeRenamed(sourcePath, this);
    }

    protected <T> void addPluginMetaData(Key<T> key, ConfigElement<T> object) {
        pluginsHelper().setObject(key, object);
        commit();
        getVFS().notifyInodeRefresh(this, false);
    }

    @SuppressWarnings("unchecked")
    protected <T> T removePluginMetaData(Key<T> key) {
        T value = pluginsHelper().remove(key);
        commit();
        getVFS().notifyInodeRefresh(this, false);
        return value;
    }

    public <T> T getPluginMetaData(Key<T> key) throws KeyNotFoundException {
        return pluginsHelper().get(key);
    }

    /*
     * (non-Javadoc)
     *
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        String ret = "[path=" + getPath() + "]" +
                "[user,group=" + getUsername() + "," + getGroup() + "]" +
                "[creationTime=" + getCreationTime() + "]" +
                "[lastModified=" + getLastModified() + "]" +
                "[size=" + getSize() + "]";
        return ret;
    }

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof VirtualFileSystemInode))
            return false;

        return ((VirtualFileSystemInode) obj).getPath().equalsIgnoreCase(getPath());
    }

    protected abstract Map<String, AtomicInteger> getSlaveRefCounts();

    /**
     * Publish a refresh notification for this inode
     */
    protected void refresh(boolean sync) {
        getVFS().notifyInodeRefresh(this, sync);
    }
}
