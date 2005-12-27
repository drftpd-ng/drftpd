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
import java.util.Properties;

/**
 * If there's a tie in the ScoreChart, the CycleFilter removes it.
 * @author fr0w
 */
public class CycleFilter extends DiskFilter {
	
	public CycleFilter(Properties p, Integer i) {
		super(p, i);
	}
	
	public void process(ScoreChart sc, String path) {
		ArrayList currentList = sc.getScoreList();
		ArrayList newList = new ArrayList();
		long maxScore = 0;
		long lastModified = System.currentTimeMillis();
		ScoreChart.RootScore bestRoot = null;
		
		// retrieves the higher score
		for (Iterator iter = currentList.iterator(); iter.hasNext();) {
			ScoreChart.RootScore rootScore = (ScoreChart.RootScore) iter.next();
			if (rootScore.getScore() >= maxScore)
				maxScore = rootScore.getScore();
		}
		
		// pick which roots have >= score then the higher score
		for (Iterator iter = currentList.iterator(); iter.hasNext();) {
			ScoreChart.RootScore rootScore = (ScoreChart.RootScore) iter.next();
			if (rootScore.getScore() >= maxScore) {
				newList.add(rootScore);
			}
		}
		
		// if just one, dont need to iterate, just add the point.
		if (newList.size() == 1) {
			((ScoreChart.RootScore) newList.get(0)).addScore(1L);
			return;
		}
		
		// pick the less recent used root
		for (Iterator iter = newList.iterator(); iter.hasNext();) {
			ScoreChart.RootScore rootScore = (ScoreChart.RootScore) iter.next();
			long l = rootScore.getRoot().lastModified();
			if (l <= lastModified) {
				lastModified = l;
				bestRoot = rootScore;
			}
		}
		
		// finally adds the point
		bestRoot.addScore(1);
	}
	
}
