package org.drftpd.mirroring.archivetypes;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.event.listeners.Archive;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.mirroring.ArchiveType;
import org.drftpd.sections.SectionInterface;

/**
 * @author zubov
 */
public class ConstantMirroring extends ArchiveType {
	private int _numOfSlaves;
	public ConstantMirroring(Archive archive, SectionInterface section, Properties p) {
		super(archive, section,p);
		_numOfSlaves = Integer.parseInt(FtpConfig.getProperty(p,section.getName() + ".numOfSlaves"));
		if (_numOfSlaves < 2) {
			throw new IllegalArgumentException("numOfSlaves has to be > 1 for section " + section.getName());
		}
	}
	public void cleanup(ArrayList jobList) {
		recursiveCleanup(getDirectory());
	}
	private void recursiveCleanup(LinkedRemoteFileInterface lrf) {
		for (Iterator iter = new ArrayList(getDirectory().getFiles()).iterator();
		iter.hasNext();
		) {
			LinkedRemoteFileInterface src =
				(LinkedRemoteFileInterface) iter.next();
			if (src.isFile()) {
				Collection slaves = new ArrayList(src.getSlaves());
				if (slaves.isEmpty()) {
					// couldn't mirror file
					continue;
				}
				Iterator offlineSlaveIter = slaves.iterator();
				while(src.getSlaves().size()>_numOfSlaves && offlineSlaveIter.hasNext()) { // remove offline slaves until size is okay
					RemoteSlave slave = (RemoteSlave) offlineSlaveIter.next();
					if (!slave.isAvailable()) {
						src.removeSlave(slave);
					}
					offlineSlaveIter.remove();
				}
				slaves = new ArrayList(src.getSlaves());
				Iterator onlineSlaveIter = slaves.iterator();
				while(slaves.size()>_numOfSlaves && onlineSlaveIter.hasNext()) { // remove online slaves until size is okay
					RemoteSlave slave = (RemoteSlave) onlineSlaveIter.next();
					src.removeSlave(slave);
					onlineSlaveIter.remove();
				}
			} else { // src.isDirectory()
				recursiveCleanup(src);
			}
		}
	}
	public HashSet findDestinationSlaves() {
		try {
			return new HashSet(_parent.getConnectionManager()
					.getSlaveManager().getAvailableSlaves());
		} catch (NoAvailableSlaveException e) {
			return null;
		}
	}
	protected boolean isArchivedDir(LinkedRemoteFileInterface lrf)
			throws IncompleteDirectoryException, OfflineSlaveException {
		for (Iterator iter = getDirectory().getFiles().iterator(); iter.hasNext();
		) {
			LinkedRemoteFileInterface src =
				(LinkedRemoteFileInterface) iter.next();
			if (src.isFile()) {
				try {
					if (src.getAvailableSlaves().size() == _numOfSlaves)
						return false;
				} catch (NoAvailableSlaveException e) {
					throw new OfflineSlaveException(src.getName() + " is not online");
				}
				if (src.getSlaves().size() > _numOfSlaves) {
					return false;
				}
			} else { // src.isDirectory()
				return isArchivedDir(src);
			}
		}
		return true;
	}

	public String toString() {
		return "ConstantMirroring=[directory=[" + getDirectory().getPath() + "]dest=[" + outputSlaves(getRSlaves()) + "]numOfSlaves=[" + _numOfSlaves + "]]";
	}
}
