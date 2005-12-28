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

public class AssignRoot {
	
	/**
	 * Parser for lines like this:<br><pre>
	 * x.assign=1+500 2+300
	 * x.assign=1+200, 2+200
	 * x.assign=1 2 (will assign 0 points to each root)
	 * x.assign=1,2 (will assign 0 points to each root)</pre>
	 */
	public static ArrayList parseAssign(String s) {
		String parse = s.trim().replaceAll(",", "");
		String[] p = parse.split(" ");
		ArrayList list = new ArrayList();
		for (int i = 0; i < p.length; i++) {
			AssignParser a = new AssignParser(p[i]);
			list.add(a);
		}		
		return list;
	}
	
	/**
	 * Checks if <code>root</code> is inside <code>list</code>
	 * that must be a <code>parseAssign()</code> ArrayList.
	 * @param root
	 * @param list
	 */
	public static boolean isAssignedRoot(Root root, ArrayList list) {
		for (Iterator iter = list.iterator(); iter.hasNext();) {
			AssignParser a = (AssignParser) iter.next();
			int i = (int) a.getRoot();
			Root o = (Root) DiskFilter.getRootList().get(i);
			if (o.equals(root)) {
				return true;
			}
		}
		return false;
	}
	
	/**
	 * Iterates throught a <code>parseAssign()</code> ArrayList
	 * and add the current assigned scores to the ScoreChart <code>sc</code>
	 * @param list
	 * @param sc
	 */
	public static void addScoresToChart(ArrayList list, ScoreChart sc) {
		for (Iterator iter = list.iterator(); iter.hasNext();) {
			AssignParser ap = (AssignParser) iter.next();
			int i = (int) ap.getRoot();
			Root o = (Root) DiskFilter.getRootList().get(i);
			sc.addScore(o, ap.getScore());
		}
	}
}

class AssignParser {
	private long _score;
	private int _root;
	
	public AssignParser(String s) {
		boolean positive;
		int pos = s.indexOf("+");
		
		if (pos != -1) {
			positive = true;
		} else {
			pos = s.indexOf("-");
			
			if (pos == -1) {
				_score = 0;
				_root = Integer.parseInt(s)-1;
				return;
			}
			
			positive = false;
		}
		
		String root = s.substring(0, pos);
		String assign = s.substring(pos + 1);

		_root = Integer.parseInt(root)-1;
		
		if (assign.equals("remove")) {
			_score = 0;
			positive = false;
		} else {
			_score = Long.parseLong(assign);
			
			
			if (!positive) {
				_score = -_score;
			}
		}
	}
	
	public int getRoot() {
		return _root;
	}
	
	public long getScore() {
		return _score;
	}
	
	public String toString() {
		return getClass() + "@" + hashCode() + "[root=" + getRoot() + ",score=" + getScore() +"]";
	}
}
