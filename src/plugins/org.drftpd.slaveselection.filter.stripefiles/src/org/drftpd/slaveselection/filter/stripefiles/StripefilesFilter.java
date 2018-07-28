package org.drftpd.slaveselection.filter.stripefiles;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.Filter;
import org.drftpd.slaveselection.filter.ScoreChart;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author scitz0
 * @description Removes all slaves except the ones with lowest number of files in the dir
 * @version $Id$
 */
public class StripefilesFilter extends Filter {

	public StripefilesFilter(int i, Properties p) {
		super(i, p);
	}

	@Override
	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface inode, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		DirectoryHandle dir;
		try {
			if (inode.isFile()) {
				dir = ((FileHandle)inode).getParent();
			} else {
				return;
			}

			HashMap<String,Integer> hm = new HashMap<>();
			for (FileHandle file : dir.getFilesUnchecked()){
				try {
					for (RemoteSlave slave : file.getAvailableSlaves()) {
						String slaveName = slave.getName();
                        hm.merge(slaveName, 1, (a, b) -> a + b);
					}
				} catch (NoAvailableSlaveException ex) {
					// Just continue
				} catch (FileNotFoundException ex) {
					// Just continue
				}
			}

			//Find slave(s) with lowest number of files
			int i = Integer.MAX_VALUE;
			for (ScoreChart.SlaveScore score : scorechart.getSlaveScores()) {
				Integer filesOnSlave = hm.get(score.getRSlave().getName());
				int files = filesOnSlave == null ? 0 : filesOnSlave;
				if (files < i)
					i = files;
			}
			//Remove all slaves with too many files
			for (Iterator iter = scorechart.getSlaveScores().iterator();
					iter.hasNext();) {
				ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iter.next();
				Integer filesOnSlave = hm.get(score.getRSlave().getName());
				if (filesOnSlave != null && filesOnSlave > i) {
					iter.remove();
				}
			}
		} catch (FileNotFoundException e) {
			// Strange... just exit
		}
	}
}
