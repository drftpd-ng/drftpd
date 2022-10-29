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
package org.drftpd.master.slaveselection.filter.stripefiles;

import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slaveselection.filter.Filter;
import org.drftpd.master.slaveselection.filter.ScoreChart;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

/**
 * @author scitz0
 * @version $Id$
 * @description Removes all slaves except the ones with lowest number of files in the dir
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
                dir = ((FileHandle) inode).getParent();
            } else {
                return;
            }

            HashMap<String, Integer> hm = new HashMap<>();
            for (FileHandle file : dir.getFilesUnchecked()) {
                try {
                    for (RemoteSlave slave : file.getAvailableSlaves()) {
                        String slaveName = slave.getName();
                        hm.merge(slaveName, 1, Integer::sum);
                    }
                } catch (NoAvailableSlaveException | FileNotFoundException ex) {
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
                 iter.hasNext(); ) {
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
