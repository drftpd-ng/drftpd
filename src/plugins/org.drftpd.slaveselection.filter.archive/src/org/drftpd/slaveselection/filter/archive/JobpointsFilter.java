package org.drftpd.slaveselection.filter.archive;

import java.net.InetAddress;
import java.util.Properties;

import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.archive.Archive;
import org.drftpd.plugins.archive.archivetypes.ArchiveHandler;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slaveselection.filter.Filter;
import org.drftpd.slaveselection.filter.ScoreChart;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author zubov
 * @description Gives points to slaves for each destination transfer they have
 *              per ArchiveType
 * @version $Id$
 */
public class JobpointsFilter extends Filter {

	private long _assign;

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
