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
package org.drftpd;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoSFVEntryException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.Serializable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;

import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.LinkedRemoteFileInterface;


/**
 * @author mog
 * @version $Id$
 */
public class SFVFile extends AbstractSFVFile {
    private transient LinkedRemoteFileInterface _companion;

    /**
	 * @param file
	 */
	public SFVFile(LightSFVFile file) {
		_entries = new HashMap(file.getEntries());
	}

	/**
     * @deprecated use getStatus().getMissing()
     */
    public int filesLeft() {
        return getStatus().getMissing();
    }

    /**
     * @return the number of files in the dir that are in the .sfv and aren't 0 bytes
     * @deprecated use getStatus()
     */
    public int finishedFiles() {
        return size() - getStatus().getMissing();

        //		int good = 0;
        //		for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
        //			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
        //			if (file.length() != 0)
        //				good++;
        //		}
        //		return good;
    }

    public SFVStatus getStatus() {
        int offline = 0;
        int present = 0;

        for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.length() != 0) {
                present++;

                if (!file.isAvailable()) {
                    offline++;
                }
            }
        }
        return new SFVStatus(size(), offline, present);
    }

    public long getChecksum(String fileName) throws NoSFVEntryException {
        Long checksum = (Long) _entries.get(fileName);

        if (checksum == null) {
            throw new NoSFVEntryException();
        }

        return checksum.longValue();
    }

    public Map getEntriesFiles() {
        LinkedRemoteFileInterface dir;

        try {
            dir = _companion.getParentFile();
        } catch (FileNotFoundException e) {
            throw new FatalException(e);
        }

        Map sfventries = getEntries();
        Map ret = new Hashtable();

        for (Iterator iter = sfventries.entrySet().iterator(); iter.hasNext();) {
            Map.Entry element = (Map.Entry) iter.next();
            String fileName = (String) element.getKey();

            LinkedRemoteFile file;

            try {
                file = (LinkedRemoteFile) dir.getFile(fileName);
            } catch (FileNotFoundException e1) {
                continue;
            }

            ret.put(file, element.getValue());
        }

        return ret;
    }

    public Collection getFiles() {
        return getEntriesFiles().keySet();
    }

    public long getTotalBytes() {
        long totalBytes = 0;

        for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
            totalBytes += ((LinkedRemoteFileInterface) iter.next()).length();
        }

        return totalBytes;
    }

    public long getTotalXfertime() {
        long totalXfertime = 0;

        for (Iterator iter = getFiles().iterator(); iter.hasNext();) {
            totalXfertime += ((LinkedRemoteFileInterface) iter.next()).getXfertime();
        }

        return totalXfertime;
    }

    public long getXferspeed() {
        if ((getTotalXfertime() / 1000) == 0) {
            return 0;
        }

        return getTotalBytes() / (getTotalXfertime() / 1000);
    }

    public void setCompanion(LinkedRemoteFileInterface companion) {
        if (_companion != null) {
            throw new IllegalStateException("Can't overwrite companion");
        }

        _companion = companion;
    }

    public static class SFVStatus {
        private int _present;
        private int _offline;
        private int _total;

        public SFVStatus(int total, int offline, int present) {
            _total = total;
            _offline = offline;
            _present = present;
        }

        /**
         * Returns the number of files that don't exist or are 0byte.
         * @return the number of files that don't exist or are 0byte.
         */
        public int getMissing() {
            return _total - _present;
        }

        /**
         * Returns the number of files that exist and are not 0 byte.
         * @return the number of files that exist and are not 0 byte.
         */
        public int getPresent() {
            return _present;
        }

        /**
         * Returns the number of files that are available (online).
         *
         * If a file is online, it is of course is also present (exists).
         * @return the number of files that are available (present & online)
         */
        public int getAvailable() {
            return _present - _offline;
        }

        /**
         * Returns the number of files that are offline.
         * @return the number of files that are offline.
         */
        public int getOffline() {
            return _offline;
        }

        public boolean isFinished() {
            return getMissing() == 0;
        }
    }
}
