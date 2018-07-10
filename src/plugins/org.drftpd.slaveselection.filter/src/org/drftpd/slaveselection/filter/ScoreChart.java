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
package org.drftpd.slaveselection.filter;

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

/**
 * @author mog
 * @version $Id$
 */
public class ScoreChart {
	private ArrayList<SlaveScore> _scoreChart;


	public ScoreChart(Collection<RemoteSlave> slaves) {
		_scoreChart = new ArrayList<>();
		for (RemoteSlave rslave : slaves) {
			_scoreChart.add(new SlaveScore(rslave));
		}
	}

	public RemoteSlave getBestSlave() throws NoAvailableSlaveException {
		return getBestSlaveScore().getRSlave();
	}

	public SlaveScore getBestSlaveScore() throws NoAvailableSlaveException {
		SlaveScore bestScore;
		
		Iterator<SlaveScore> iter = getSlaveScores().iterator();

		if (isEmpty()) {
			throw new NoAvailableSlaveException();
		}

		bestScore = iter.next();

		while (iter.hasNext()) {
			SlaveScore score = iter.next();

			if (score.getScore() > bestScore.getScore()) {
				bestScore = score;
			}
		}

		if (bestScore == null) {
			throw new NoAvailableSlaveException();
		}

		return bestScore;
	}

	/**
	 * Returns the SlaveScore entry for the RemoteSlave rslave.
	 */
	public SlaveScore getScoreForSlave(RemoteSlave rslave) throws ObjectNotFoundException {
		for (SlaveScore score : getSlaveScores()) {
			if (score.getRSlave().equals(rslave)) {
				return score;
			}
		}

		throw new ObjectNotFoundException(rslave.getName() + " not in ScoreChart");
	}

	/**
	 * Returns the Collection holding the {@link SlaveScore} elements.
	 */
	public ArrayList<SlaveScore> getSlaveScores() {
		return _scoreChart;
	}

	public void removeSlaveFromChart(RemoteSlave rslave) {
        _scoreChart.removeIf(score -> score.getRSlave().equals(rslave));
	}
	
	public void addScoreToSlave(RemoteSlave rslave, long score) throws ObjectNotFoundException {
		getScoreForSlave(rslave).addScore(score);
	}

	public static class SlaveScore implements Comparable<SlaveScore> {
		private RemoteSlave _rslave;
		private long _score;

		public SlaveScore(RemoteSlave rslave) {
			_rslave = rslave;
		}

		public void addScore(long score) {
			_score += score;
		}

		public int compareTo(SlaveScore s) {
			return (Long.compare(getScore(), s.getScore()));
		}

		public RemoteSlave getRSlave() {
			return _rslave;
		}

		public long getScore() {
			return _score;
		}

		public String toString() {
			return "SlaveScore[rslave=" + getRSlave().getName() + ",score="+ getScore() + "]";
		}
	}

	public boolean isEmpty() {
		return getSlaveScores().isEmpty();
	}
}
