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
package net.sf.drftpd.master;


/**
 * @author mog
 * @version $Id: UploaderPosition.java,v 1.8 2004/11/11 14:58:32 mog Exp $
 */
public class UploaderPosition implements Comparable {
    long _bytes;
    int _files;
    String _username;
    long _xfertime;

    public UploaderPosition(String username, long bytes, int files,
        long xfertime) {
        this._username = username;
        this._bytes = bytes;
        this._files = files;
        this._xfertime = xfertime;
    }

    public int compareTo(Object o) {
        return compareTo((UploaderPosition) o);
    }

    /** Sorts in reverse order so that the biggest shows up first.
     * @see java.lang.Comparable#compareTo(java.lang.Object)
     */
    public int compareTo(UploaderPosition o) {
        long thisVal = getBytes();
        long anotherVal = o.getBytes();

        return ((thisVal < anotherVal) ? 1 : ((thisVal == anotherVal) ? 0 : (-1)));
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    public boolean equals(Object obj) {
        //if(obj instanceof String && obj.equals(getUsername())) return true;
        if (!(obj instanceof UploaderPosition)) {
            return false;
        }

        UploaderPosition other = (UploaderPosition) obj;

        return getUsername().equals(other.getUsername());
    }

    public long getBytes() {
        return this._bytes;
    }

    public int getFiles() {
        return this._files;
    }

    public String getUsername() {
        return _username;
    }

    public long getXferspeed() {
        if (getXfertime() == 0) {
            return 0;
        }

        return (long) (getBytes() / (getXfertime() / 1000.0));
    }

    public long getXfertime() {
        return _xfertime;
    }

    public int hashCode() {
        return getUsername().hashCode();
    }

    public void updateBytes(long bytes) {
        this._bytes += bytes;
    }

    public void updateFiles(int files) {
        this._files += files;
    }

    public void updateXfertime(long xfertime) {
        this._xfertime += xfertime;
    }
}
