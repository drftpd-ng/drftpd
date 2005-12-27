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

package org.drftpd.slave.diskselection;

import java.util.ArrayList;
import java.util.Iterator;

import org.drftpd.slave.Root;
import org.drftpd.slave.RootCollection;

/**
 * Keep RootScore organized
 * @author fr0w
 */
public class ScoreChart {

	private RootCollection _rootCollection;
	private ArrayList _scoreList;
	
	/**
	 * Creates the ArrayList<RootScore>
	 * @param rootCollection
	 */
	public ScoreChart(RootCollection rootCollection) {
		_rootCollection = rootCollection;
		
		Iterator iter = _rootCollection.iterator();
		_scoreList = new ArrayList();
		while (iter.hasNext()) {
			Root o = (Root) iter.next();
			_scoreList.add(new RootScore(o, 0));
		}
	}
	
	/**
	 * @param root
	 * @return <code>root</code> score.
	 */
	public long getRootScore(Root root) {
		return getRootScoreObject(root).getScore();
	}
	
	/**
	 * @return ArrayList<RootScore>
	 */
	public ArrayList getScoreList() {
		return _scoreList;
	}
	
	/**
	 * Adds <code>score</score> to <code>root</code>.
	 * @param root
	 * @param score
	 */
	public void addScore(Root root, long score) {
		getRootScoreObject(root).addScore(score);
	}
	
	/**
	 * Removes <code>root</code> from the ScoreChart.
	 * @param root
	 */
	public void removeRootScore(Root root) {
		_scoreList.remove(getRootScoreObject(root));
	}
	
	/**
	 * Iterates thought the ScoreChart and trying to match <code>root</code> 
	 * @param root
	 */
	public RootScore getRootScoreObject(Root root) {
		for (Iterator iter = _scoreList.iterator(); iter.hasNext();) {
			RootScore rootScore = (RootScore) iter.next();
			Root o = rootScore.getRoot();
			if (o.equals(root))
				return rootScore;
		}
		// should never happend;
		return null;
	}
	
	public class RootScore {
		
		private Root _root;
		private long _score;
		
		public RootScore(Root root, long score) {
			_root = root;
			_score = score;
		}
		
		public RootScore(Object o, long score) {
			this((Root) o, score);
		}
		
		public void addScore(long score) {
			_score += score;
		}
		
		public long getScore() {
			return _score;
		}
		
		public Root getRoot() {
			return _root;
		}
	}
}
