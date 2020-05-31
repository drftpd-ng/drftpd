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

package org.drftpd.archive.master.slaveselection;

import org.drftpd.archive.master.Archive;
import org.drftpd.archive.master.archivetypes.ArchiveHandler;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slaveselection.filter.Filter;
import org.drftpd.master.slaveselection.filter.ScoreChart;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id$
 * @description Takes points from slaves if they don't have enough space to hold
 * the contents of the Archive Directory
 */
public class DirectoryspaceFilter extends Filter {

    private final long _assign;

    public DirectoryspaceFilter(int i, Properties p) {
        super(i, p);
        _assign = Long.parseLong(PropertyHelper.getProperty(p, i + ".assign"));
    }

    @Override
    public void process(ScoreChart scorechart, User user, InetAddress peer,
                        char direction, InodeHandleInterface inode, RemoteSlave sourceSlave)
            throws NoAvailableSlaveException {
        SectionInterface section = GlobalContext.getGlobalContext()
                .getSectionManager().lookup(((InodeHandle) inode).getParent());
        Archive archive = null;
        for (PluginInterface plugin : GlobalContext.getGlobalContext()
                .getPlugins()) {
            if (plugin instanceof Archive) {
                archive = (Archive) plugin;
                break;
            }
        }
        if (archive == null) {
            // Archive is not loaded
            return;
        }
        DirectoryHandle directory = null;
        for (ArchiveHandler handler : archive.getArchiveHandlers()) {
            if (handler.getArchiveType().getSection().equals(section)) {
                directory = handler.getArchiveType().getDirectory();
            }
        }
        if (directory == null) {
            // not being transferred by Archive
            return;
        }
        try {
            long freeSpaceNeeded = directory.getSize();
            ArrayList<FileHandle> files = directory
                    .getAllFilesRecursiveUnchecked();
            for (Iterator<ScoreChart.SlaveScore> iter = scorechart
                    .getSlaveScores().iterator(); iter.hasNext(); ) {
                ScoreChart.SlaveScore slaveScore = iter.next();
                RemoteSlave rslave = slaveScore.getRSlave();
                long rslaveHasFilesSize = 0L;
                for (FileHandle file : files) {
                    try {
                        if (file.getSlaveNames().contains(rslave.getName())) {
                            rslaveHasFilesSize += file.getSize();
                        }
                    } catch (FileNotFoundException e) {
                        // couldn't find that file, do nothing
                        // continue on
                    }
                }
                try {
                    if (rslave.getSlaveStatus().getDiskSpaceAvailable()
                            + rslaveHasFilesSize < freeSpaceNeeded) {
                        slaveScore.addScore(-_assign);
                    }
                } catch (SlaveUnavailableException e) {
                    // we can remove the slave
                    iter.remove();
                }
            }
        } catch (FileNotFoundException e) {
            // can't do anything, couldn't find the directory
        }
    }

}
