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

import org.drftpd.slave.Root;

import java.util.ArrayList;

/**
 * @author fr0w
 * @version $Id$
 */
public class AssignRoot {
	/**
	 * Parser for lines like this:<br>
	 * 
	 * <pre>
	 *  x.assign=1+500 2+300
	 *  x.assign=1+200, 2+200
	 *  x.assign=1 2 (will assign 0 points to each root)
	 *  x.assign=1,2 (will assign 0 points to each root)
	 *  x.assign=all (will assign ALL roots w/ 0 points each.).
	 * </pre>
	 */
	public static ArrayList<AssignParser> parseAssign(DiskFilter filter, String s) {
		String parse = s.trim().replaceAll(",", "");
		String[] p = parse.split(" ");
		
		ArrayList<AssignParser> list = new ArrayList<>();
		
		// safety precaution.
		int x = filter.getDiskSelection().getRootCollection().getRootList().size();

        for (String aP : p) {
            AssignParser a = new AssignParser(aP);

            // Root index is bigger than the root list that means that it does not exist.
            if (a.getRoot() > x)
                throw new IllegalArgumentException("You are trying to assign points to a root that doesn't exists.");

            list.add(a);
        }
		return list;
	}

	/**
	 * Checks if <code>root</code> is inside <code>list</code> that must be
	 * a <code>parseAssign()</code> ArrayList.
	 * 
	 * @param root
	 * @param list
	 */
	public static boolean isAssignedRoot(DiskFilter filter, Root root, ArrayList<AssignParser> list) {
		for (AssignParser a : list) {			
			if (a.allAssigned())
				return true;
			
			int i = a.getRoot();
			Root o = filter.getRootList().get(i-1);
			if (o.equals(root)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Iterates throught a <code>parseAssign()</code> ArrayList and add the
	 * current assigned scores to the ScoreChart <code>sc</code>
	 * 
	 * @param list
	 * @param sc
	 */
	public static void addScoresToChart(DiskFilter filter, ArrayList<AssignParser> list, ScoreChart sc) {
		for (AssignParser ap : list) {
			
			if (ap.allAssigned())
				return;
			
			int i = ap.getRoot();
			
			Root r = filter.getRootList().get(i-1);			

			if (ap.getScore() == Integer.MIN_VALUE) {
				sc.removeFromChart(r);
			} else {
				sc.addScore(r, ap.getScore());		
			}
		}
	}
}