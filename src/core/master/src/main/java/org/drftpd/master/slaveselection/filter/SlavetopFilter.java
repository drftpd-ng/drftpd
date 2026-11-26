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
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

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
        // Find the release directory: the child of the section base directory that contains dir.
        // Outside of any section (or at the root) there is no release, so the scores are left alone.
        if (dir.getPath().equals(VirtualFileSystem.separator)) {
            return;
        }
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
        dir = findReleaseDir(dir, section.getBaseDirectory());
        if (dir == null) {
            // this is not a "release", don't process
            return;
        }

        // The per-slave file counts come from the VFS slave ref counts of the release directory
        // (recursive number of files per slave) instead of walking every file in the release.
        // If they ever drift, SITE FIXSLAVECOUNT rebuilds them.
        Map<String, Integer> counts = new HashMap<>();
        try {
            for (Map.Entry<String, AtomicInteger> entry : dir.getSlaveRefCounts().entrySet()) {
                counts.put(entry.getKey(), entry.getValue().get());
            }
        } catch (FileNotFoundException e) {
            // directory was removed, not much we can do
            return;
        }
        assignTopSlaves(scorechart, counts, _topslaves, _assign);
    }

    /**
     * Walks up from dir to the directory directly below the section base directory.
     *
     * @return that release directory, or null if dir is not below the base directory
     * (reaching the root without finding it, e.g. a directory outside of any section)
     */
    static DirectoryHandle findReleaseDir(DirectoryHandle dir, DirectoryHandle baseDir) {
        while (!dir.getPath().equals(VirtualFileSystem.separator)) {
            DirectoryHandle parent = dir.getParent();
            if (parent.equals(baseDir)) {
                return dir;
            }
            dir = parent;
        }
        return null;
    }

    /**
     * Scores every slave in the chart by its file count and gives the top slaves the bonus.
     *
     * @param counts number of files per slave name; slaves without an entry count as 0 and
     *               names not in the score chart are ignored
     */
    static void assignTopSlaves(ScoreChart scorechart, Map<String, Integer> counts, int topslaves, long assign) {
        // Hashtable kept on purpose: its iteration order decides ties between equal scores below.
        Hashtable<RemoteSlave, ScoreChart.SlaveScore> slavesmap =
                new Hashtable<>();

        for (ScoreChart.SlaveScore slaveScore : scorechart.getSlaveScores()) {
            ScoreChart.SlaveScore score = new ScoreChart.SlaveScore(slaveScore.getRSlave());
            Integer count = counts.get(slaveScore.getRSlave().getName());
            if (count != null) {
                score.addScore(count);
            }
            slavesmap.put(slaveScore.getRSlave(), score);
        }

        ArrayList<ScoreChart.SlaveScore> slavescores =
                new ArrayList<>(slavesmap.values());
        slavescores.sort(Collections.reverseOrder());

        if (assign == 0) {
            for (ScoreChart.SlaveScore score : slavescores.subList(topslaves, slavescores.size())) {
                scorechart.removeSlaveFromChart(score.getRSlave());
            }
        }
        Iterator<ScoreChart.SlaveScore> iter = slavescores.iterator();
        for (int i = 0; i < topslaves && iter.hasNext(); i++) {
            ScoreChart.SlaveScore score = iter.next();

            try {
                scorechart.getScoreForSlave(score.getRSlave()).addScore(assign);
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
