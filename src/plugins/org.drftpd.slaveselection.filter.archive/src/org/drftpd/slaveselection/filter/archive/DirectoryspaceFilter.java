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

package org.drftpd.slaveselection.filter.archive;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.plugins.archive.archivetypes.ArchiveHandler;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slaveselection.filter.Filter;
import org.drftpd.slaveselection.filter.ScoreChart;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author zubov
 * @description Takes points from slaves if they don't have enough space to hold
 *              the contents of the Archive Directory
 * @version $Id$
 */
public class DirectoryspaceFilter extends Filter {

	private long _assign;

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
					.getSlaveScores().iterator(); iter.hasNext();) {
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
