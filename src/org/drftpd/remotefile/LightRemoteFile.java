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

import net.sf.drftpd.remotefile.AbstractRemoteFile;

import java.io.FileNotFoundException;
import java.io.Serializable;

import java.util.Collection;


/**
 * @author zubov
 * @version $Id: LightRemoteFile.java,v 1.2 2004/11/02 07:32:52 zubov Exp $
 * For use in sending the filelist from the slave to the master
 */
public final class LightRemoteFile extends AbstractRemoteFile
    implements Serializable {
    private String filename;
    private long lastModified;
    private long length;

    public LightRemoteFile(String filename, long lastModified, long length) {
        this.filename = filename;
        this.lastModified = lastModified;
        this.length = length;
    }

    public Collection getFiles() {
        throw new UnsupportedOperationException();
    }

    public String getParent() throws FileNotFoundException {
        throw new UnsupportedOperationException();
    }

    public String getPath() {
        throw new UnsupportedOperationException();
    }

    public Collection getSlaves() {
        throw new UnsupportedOperationException();
    }

    public boolean isDirectory() {
        return false;
    }

    public boolean isFile() {
        return true;
    }

    public long lastModified() {
        return lastModified;
    }

    public long length() {
        return length;
    }

    public String getName() {
        return filename;
    }
}
