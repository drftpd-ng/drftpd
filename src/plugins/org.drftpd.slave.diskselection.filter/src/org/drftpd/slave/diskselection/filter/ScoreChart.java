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

package org.drftpd.slave.diskselection.filter;

import java.util.ArrayList;

import org.drftpd.slave.Root;
import org.drftpd.slave.RootCollection;

/**
 * Keep RootScore organized
 * 
 * @author fr0w
 * @version $Id$
 */
public class ScoreChart {

	private RootCollection _rootCollection;

	private ArrayList<RootScore> _scoreList;

	/**
	 * Creates the ArrayList<RootScore>
	 * 
	 * @param rootCollection
	 */
	public ScoreChart(RootCollection rootCollection) {
		_rootCollection = rootCollection;
		_scoreList = new ArrayList<>();

		for (Root root : _rootCollection.getRootList()) {
			_scoreList.add(new RootScore(root, 0));
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
	public ArrayList<RootScore> getScoreList() {
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
	 * 
	 * @param root
	 */
	public void removeFromChart(Root root) {
		_scoreList.remove(getRootScoreObject(root));
	}

	/**
	 * Iterates thought the ScoreChart and trying to match <code>root</code>
	 * 
	 * @param root
	 */
	public RootScore getRootScoreObject(Root root) {
		if (root == null) {
			throw new IllegalArgumentException("Argument to getRootScoreObject() cannot be null");
		}
		
		for (RootScore rootScore : _scoreList) {
			Root o = rootScore.getRoot();
			if (o.equals(root))
				return rootScore;
		}
		// should never happen;
		return null;
	}
	
	public String toString() {
		StringBuilder sb = new StringBuilder();
		sb.append(getClass().getName()+'[');
		
		for (int i = 0; i < _scoreList.size(); i++) {
			RootScore rs = _scoreList.get(i);
			sb.append(rs.getRoot().toString()+'='+rs.getScore());
			
			if (i+1 != _scoreList.size()) {
				sb.append(',');
			}
		}
		
		sb.append(']');
		
		return sb.toString();
	}

	public static class RootScore {

		private Root _root;

		private long _score;

		public RootScore(Root root, long score) {
			_root = root;
			_score = score;
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
