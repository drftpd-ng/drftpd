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
package org.drftpd.remotefile;

import java.io.FileNotFoundException;

import java.util.Collection;


/**
 * @author mog
 * @version $Id: RemoteFileInterface.java,v 1.1 2004/11/09 18:59:57 mog Exp $
 */
public interface RemoteFileInterface {
    /**
     * Returns the cached checksum or 0 if no checksum was cached.
     * <p>
     * Use {getCheckSum()} to automatically calculate checksum if no cached checksum is available.
     */
    public long getCheckSumCached();

    /**
     * Returns a Collection of RemoteFileInterface objects.
     */
    public Collection getFiles();

    /**
     * Get the group owner of the file as a String.
     * <p>
     * getUser().getGroupname() if the implementing class uses a User object.
     * @return primary group of the owner of this file
     */
    public String getGroupname();

    //    /**
    //     * Returns the target of the link.
    //     * @return target of the link.
    //     * @see #isLink()
    //     */
    //    public RemoteFileInterface getLink() throws FileNotFoundException;
    public String getLinkPath();

    /**
     * @see java.io.File#getName()
     */
    public String getName();

    public abstract String getParent() throws FileNotFoundException;

    public abstract String getPath();

    public Collection getSlaves();

    /**
     * Returns string representation of the owner of this file.
     * <p>
     * getUser().getUsername() if the implementing class uses a User object.
     * @return username of the owner of this file.
     */
    public String getUsername();

    public long getXfertime();

    /**
     * @see java.io.File#isDirectory()
     */
    public boolean isDirectory();

    /**
     * @see java.io.File#isFile()
     */
    public boolean isFile();

    /**
     * boolean flag whether this file is a 'link', it can be linked to another file.
     * This is for the moment used for "ghost files".
     */
    public boolean isLink();

    /**
     * @see java.io.File#lastModified()
     */
    public long lastModified();

    /**
     * @see java.io.File#length()
     */
    public long length();
}
