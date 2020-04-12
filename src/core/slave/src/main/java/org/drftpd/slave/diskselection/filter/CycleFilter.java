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

import java.util.Properties;

/**
 * If there's a tie in the ScoreChart, the CycleFilter removes it.
 * 
 * @author fr0w
 * @author zubov
 * @version $Id$
 */
public class CycleFilter extends DiskFilter {

	public CycleFilter(DiskSelectionFilter diskSelection, Properties p, Integer i) {
		super(diskSelection, p, i);
	}

	public void process(ScoreChart sc, String path) {
		ScoreChart.RootScore bestRoot = null;

		// retrieves the higher score
		for (ScoreChart.RootScore rootScore : sc.getScoreList()) {
			if (bestRoot == null) {
				bestRoot = rootScore;
			} else {
				if (rootScore.getScore() > bestRoot.getScore()) {
					bestRoot = rootScore;
				} else {
					if (rootScore.getScore() == bestRoot.getScore()
							&& bestRoot.getRoot().lastModified() > rootScore
									.getRoot().lastModified()) {
						bestRoot = rootScore;
					}
				}
			}
		}
		if (bestRoot != null) {
			// No root's have been found
			bestRoot.addScore(1);
		}
	}
}
