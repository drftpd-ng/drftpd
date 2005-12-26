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

import java.util.Properties;
import java.util.Random;

import org.drftpd.slave.Root;

/**
 * This filter simply pick a random root and
 * adds 1 point to the current ScoreChart making
 * files spread throught all roots.
 * @author fr0w
 */
public class RandomspreadFilter extends DiskFilter {
	
	public RandomspreadFilter(Properties p, Integer i) {
		super(p, i);
	}

	private Random _rand = new Random();

	public void process(ScoreChart sc, String path) {
		int i = _rand.nextInt(getRootList().size());
		Root root = (Root) getRootList().get(i);
		sc.addScore(root, 1L);		
	}
}
