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
package org.drftpd.master.slaveselection.filter;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.*;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SlavetopFilter extends Filter {
    private static final Logger logger = LogManager.getLogger(SlavetopFilter.class
            .getName());
    private final long _assign;
    private final int _topslaves;

    public SlavetopFilter(int i, Properties p) {
        super(i, p);
        // topslaves is the # of slaves to assign points to
        _topslaves = Integer.parseInt(PropertyHelper.getProperty(p, i +
                ".topslaves"));
        _assign = Long.parseLong(PropertyHelper.getProperty(p, i + ".assign"));
    }

    public void process(ScoreChart scorechart, DirectoryHandle dir) {
        //// find the section part of the path name
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
        while (!dir.getParent().equals(section.getBaseDirectory())) {
            if (dir.equals(GlobalContext.getGlobalContext().getRoot())) {
                // this is not a "release", don't process
                return;
            }
            dir = dir.getParent();
        }

        Hashtable<RemoteSlave, ScoreChart.SlaveScore> slavesmap =
                new Hashtable<>();

        for (ScoreChart.SlaveScore slaveScore : scorechart.getSlaveScores()) {
            slavesmap.put(slaveScore.getRSlave(), new ScoreChart.SlaveScore(
                    slaveScore.getRSlave()));
        }

        ArrayList<FileHandle> files = dir.getAllFilesRecursiveUnchecked();

        for (FileHandle file : files) {
            try {
                for (RemoteSlave rslave : file.getSlaves()) {
                    ScoreChart.SlaveScore score = slavesmap.get(rslave);
                    if (score == null) {
                        // the slave was already removed from SlaveSelection
                        continue;
                    }
                    score.addScore(1);
                }
            } catch (FileNotFoundException e) {
                // file was removed, not much we can do, just continue
            }
        }
        ArrayList<ScoreChart.SlaveScore> slavescores =
                new ArrayList<>(slavesmap.values());
        ArrayList<ScoreChart.SlaveScore> ss = slavescores;
        ss.sort(Collections.reverseOrder());


        if (_assign == 0) {
            for (ScoreChart.SlaveScore score : slavescores.subList(_topslaves, slavescores.size())) {
                scorechart.removeSlaveFromChart(score.getRSlave());
            }
        }
        Iterator<ScoreChart.SlaveScore> iter = slavescores.iterator();
        for (int i = 0; i < _topslaves && iter.hasNext(); i++) {
            ScoreChart.SlaveScore score = iter.next();

            try {
                scorechart.getScoreForSlave(score.getRSlave()).addScore(_assign);
            } catch (ObjectNotFoundException e1) {
                logger.error("Unable to assign points to slave {}", score.getRSlave().getName());
            }
        }
    }

    @Override
    public void process(ScoreChart scorechart, User user, InetAddress peer, char direction, InodeHandleInterface inode, RemoteSlave sourceSlave) throws NoAvailableSlaveException {
        process(scorechart, ((InodeHandle) inode).getParent());
    }
}
