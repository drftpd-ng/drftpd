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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.archive.master.Archive;
import org.drftpd.archive.master.archivetypes.ArchiveHandler;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.GlobalContext;
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
import java.util.List;
import java.util.Properties;


/**
 * @author zubov
 * @version $Id$
 * @description Takes points from slaves if they don't have enough space to hold the contents of the Archive Directory
 */
public class DirectoryspaceFilter extends Filter {

    private static final Logger logger = LogManager.getLogger(DirectoryspaceFilter.class.getName());

    private final long _assign;

    public DirectoryspaceFilter(int i, Properties p) {
        super(i, p);
        _assign = Long.parseLong(PropertyHelper.getProperty(p, i + ".assign"));
    }

    @Override
    public void process(ScoreChart scorechart, User user, InetAddress peer, char direction, InodeHandleInterface inode, RemoteSlave sourceSlave) {

        // Get the section
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(((InodeHandle) inode).getParent());

        // Get our Archive Manager
        Archive archive = null;
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof Archive) {
                archive = (Archive) plugin;
                break;
            }
        }
        if (archive == null) {
            // Archive is not loaded
            logger.debug("Not doing anything as Archive plugin is not loaded");
            return;
        }

        // Get a list of active and/or pending archive actions for section
        List<DirectoryHandle> dirHandlers = new ArrayList<>();
        for (ArchiveHandler handler : archive.getArchiveHandlers()) {
            if (handler.getArchiveType().getSection().equals(section)) {
                DirectoryHandle dirHandle = handler.getArchiveType().getDirectory();
                // if we do not have a directory handler the archiveType is still pending so we ignore
                if (dirHandle != null) {
                    dirHandlers.add(dirHandle);
                }
            }
        }
        logger.debug("We found {} active and/or pending archive actions", dirHandlers.size());
        if (dirHandlers.size() == 0) {
            // not being transferred by Archive
            logger.debug("Ignoring this request as there are no archive handlers active for section {}", section.getName());
            return;
        }
        // Check each slave individually
        for (Iterator<ScoreChart.SlaveScore> slaveScoreIterator = scorechart.getSlaveScores().iterator(); slaveScoreIterator.hasNext(); ) {
            ScoreChart.SlaveScore slaveScore = slaveScoreIterator.next();
            RemoteSlave slave = slaveScore.getRSlave();
            long slaveHasFilesSize = 0L;
            long freeSpaceNeeded = 0L;
            for (DirectoryHandle dirHandle : dirHandlers) {
                long dirHandleSize = 0L;
                try {
                    dirHandleSize = dirHandle.getSize();
                } catch (FileNotFoundException e) {
                    // can't do anything, couldn't find the directory
                    logger.debug("We received a FileNotFoundException for a active and/or pending archive action. Ignoring as the directory might have been removed already", e);
                }
                if (dirHandleSize == 0L) {
                    // If we cannot find the size, ignore
                    continue;
                }
                freeSpaceNeeded += dirHandleSize;
                ArrayList<FileHandle> files = dirHandle.getAllFilesRecursiveUnchecked();
                for (FileHandle file : files) {
                    try {
                        if (file.getSlaveNames().contains(slave.getName())) {
                            slaveHasFilesSize += dirHandleSize;
                        }
                    } catch (FileNotFoundException e) {
                        // couldn't find that file, do nothing and continue on
                        logger.debug("Unable to find file {} on any slave, this could happen if the file/directory was deleted before we got here", file.getName());
                    }
                }
            }
            try {
                long freeSpaceAvailable = slave.getSlaveStatus().getDiskSpaceAvailable();
                logger.debug("freeSpaceNeeded: {}, slaveHasFilesSize: {}, availableSpace: {}", freeSpaceNeeded, slaveHasFilesSize, freeSpaceAvailable);
                if ((freeSpaceAvailable + slaveHasFilesSize) < freeSpaceNeeded) {
                    logger.debug("Adding -{} score to slave {}", _assign, slave.getName());
                    slaveScore.addScore(-_assign);
                }
            } catch (SlaveUnavailableException e) {
                // we can remove the slave
                logger.debug("Removing slave {} as it is offline", slave.getName());
                slaveScoreIterator.remove();
            }
        }
    }

}
