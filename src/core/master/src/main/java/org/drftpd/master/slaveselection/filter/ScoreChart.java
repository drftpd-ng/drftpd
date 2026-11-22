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

import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * @author mog
 * @version $Id$
 */
public class ScoreChart {

    private final ArrayList<SlaveScore> _scoreChart;

    public ScoreChart(Collection<RemoteSlave> slaves) {
        _scoreChart = new ArrayList<>();
        for (RemoteSlave rslave : slaves) {
            _scoreChart.add(new SlaveScore(rslave));
        }
    }

    public RemoteSlave getBestSlave() throws NoAvailableSlaveException {
        return getBestSlaveScore().getRSlave();
    }

    /**
     * Returns the slave with the highest score. Ties are broken by picking the slave
     * that was least recently used for the given direction, then at random.
     */
    public RemoteSlave getBestSlave(char direction) throws NoAvailableSlaveException {
        return getBestSlaveScore(direction).getRSlave();
    }

    public SlaveScore getBestSlaveScore() throws NoAvailableSlaveException {
        return getBestSlaveScore(Transfer.TRANSFER_UNKNOWN);
    }

    /**
     * Returns the highest SlaveScore. When several slaves share the top score, the one
     * least recently used for <code>direction</code> wins; if that is tied too, one of them
     * is picked at random so no slave is favoured by its position in the chart.
     */
    public SlaveScore getBestSlaveScore(char direction) throws NoAvailableSlaveException {
        if (isEmpty()) {
            throw new NoAvailableSlaveException();
        }

        ArrayList<SlaveScore> best = new ArrayList<>();
        long bestValue = Long.MIN_VALUE;

        for (SlaveScore score : getSlaveScores()) {
            if (best.isEmpty() || score.getScore() > bestValue) {
                best.clear();
                best.add(score);
                bestValue = score.getScore();
            } else if (score.getScore() == bestValue) {
                best.add(score);
            }
        }

        if (best.size() == 1) {
            return best.get(0);
        }

        ArrayList<SlaveScore> leastUsed = new ArrayList<>();
        long oldest = Long.MAX_VALUE;

        for (SlaveScore score : best) {
            long last = score.getRSlave().getLastTransferForDirection(direction);
            if (last < oldest) {
                leastUsed.clear();
                leastUsed.add(score);
                oldest = last;
            } else if (last == oldest) {
                leastUsed.add(score);
            }
        }

        if (leastUsed.size() == 1) {
            return leastUsed.get(0);
        }

        return leastUsed.get(ThreadLocalRandom.current().nextInt(leastUsed.size()));
    }

    /**
     * Returns the SlaveScore entry for the RemoteSlave rslave.
     */
    public SlaveScore getScoreForSlave(RemoteSlave remoteSlave) throws ObjectNotFoundException {
        for (SlaveScore score : getSlaveScores()) {
            if (score.getRSlave().equals(remoteSlave)) {
                return score;
            }
        }

        throw new ObjectNotFoundException(remoteSlave.getName() + " not in ScoreChart");
    }

    /**
     * Returns the Collection holding the {@link SlaveScore} elements.
     */
    public ArrayList<SlaveScore> getSlaveScores() {
        return _scoreChart;
    }

    public void removeSlaveFromChart(RemoteSlave remoteSlave) {
        _scoreChart.removeIf(score -> score.getRSlave().equals(remoteSlave));
    }

    public void addScoreToSlave(RemoteSlave remoteSlave, long score) throws ObjectNotFoundException {
        getScoreForSlave(remoteSlave).addScore(score);
    }

    public boolean isEmpty() {
        return getSlaveScores().isEmpty();
    }

    public static class SlaveScore implements Comparable<SlaveScore> {
        private final RemoteSlave _remoteSlave;
        private long _score;

        public SlaveScore(RemoteSlave rslave) {
            _remoteSlave = rslave;
        }

        public void addScore(long score) {
            _score += score;
        }

        public int compareTo(SlaveScore s) {
            return (Long.compare(getScore(), s.getScore()));
        }

        public RemoteSlave getRSlave() {
            return _remoteSlave;
        }

        public long getScore() {
            return _score;
        }

        public String toString() {
            return "SlaveScore[remoteSlave=" + getRSlave().getName() + ",score=" + getScore() + "]";
        }
    }
}
