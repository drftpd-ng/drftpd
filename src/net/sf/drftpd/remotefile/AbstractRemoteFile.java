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


/**
 * @author mog
 * @version $Id: AbstractRemoteFile.java,v 1.2 2004/08/03 20:14:02 zubov Exp $
 */
public abstract class AbstractRemoteFile implements RemoteFileInterface {
    public boolean equals(Object file) {
        if (!(file instanceof RemoteFileInterface)) {
            return false;
        }

        return getPath().equals(((AbstractRemoteFile) file).getPath());
    }

    public String getGroupname() {
        return "drftpd";
    }

    public RemoteFileInterface getLink() {
        throw new UnsupportedOperationException();
    }

    public String getUsername() {
        return "drftpd";
    }

    public long getXfertime() {
        throw new UnsupportedOperationException();
    }

    public boolean isDeleted() {
        return false;
    }

    public int hashCode() {
        return getName().hashCode();
    }

    public boolean isLink() {
        return false;
    }

    public String toString() {
        StringBuffer ret = new StringBuffer();
        ret.append(getClass().getName() + "[");

        if (isDirectory()) {
            ret.append("[directory: true]");
        }

        if (isFile()) {
            ret.append("[file: true]");
        }

        ret.append("[length(): " + this.length() + "]");
        ret.append(getPath());
        ret.append("]");

        return ret.toString();
    }

    public String getLinkPath() {
        return getLink().getPath();
    }

    public long getCheckSumCached() {
        return 0;
    }
}
