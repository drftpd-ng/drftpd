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
package org.drftpd.imdb.master;

import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.imdb.master.vfs.IMDBVFSDataNFO;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;

/**
 * @author scitz0
 */
public class IMDBPrintThread extends Thread {
    private final DirectoryHandle _dir;
    private final SectionInterface _section;

    public IMDBPrintThread(DirectoryHandle dir, SectionInterface section) {
        setPriority(Thread.MIN_PRIORITY);
        _dir = dir;
        _section = section;
    }

    @Override
    public void run() {
        IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO(_dir);
        IMDBInfo imdbInfo = imdbData.getIMDBInfoFromCache();
        int sleep = 0; // Time to wait for IMDB data
        while (sleep < 20 && imdbInfo == null) {
            sleep++;
            try {
                sleep(1000);
            } catch (InterruptedException ie) {
                // Thread interrupted
                break;
            }
            imdbInfo = imdbData.getIMDBInfoFromCache();
        }
        if (imdbInfo != null) {
            IMDBUtils.publishEvent(imdbInfo, _dir, _section);
        }
    }
}