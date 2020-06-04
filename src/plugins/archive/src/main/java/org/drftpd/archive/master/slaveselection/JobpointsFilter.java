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
import org.drftpd.jobs.master.Job;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slaveselection.filter.Filter;
import org.drftpd.master.slaveselection.filter.ScoreChart;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.net.InetAddress;
import java.util.Properties;

/**
 * @author zubov
 * @version $Id$
 * @description Gives points to slaves for each destination transfer they have
 * per ArchiveType
 */
public class JobpointsFilter extends Filter {

    private final long _assign;

    public JobpointsFilter(int i, Properties p) {
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
        ArchiveHandler archiveHandler = null;
        for (ArchiveHandler handler : archive.getArchiveHandlers()) {
            if (handler.getArchiveType().getSection().equals(section)) {
                archiveHandler = handler;
            }
        }
        if (archiveHandler == null) {
            // Could not find archiveHandler for release
            return;
        }
        for (Job job : archiveHandler.getJobs()) {
            try {
                RemoteSlave rslave = job.getDestinationSlave();
                try {
                    scorechart.getScoreForSlave(rslave).addScore(_assign);
                } catch (ObjectNotFoundException e) {
                    // slave was not in the destination list... okay, nevermind
                    // :)
                }
            } catch (IllegalStateException e) {
                // job wasn't transferring, just continue on, give no points
            }
        }
    }

}
