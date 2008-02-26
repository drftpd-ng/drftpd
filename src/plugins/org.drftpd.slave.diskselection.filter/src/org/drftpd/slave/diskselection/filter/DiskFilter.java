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
import java.util.Properties;

import org.drftpd.slave.Root;

/**
 * Generic interface.
 * @version $Id$
 * @author fr0w
 */
public abstract class DiskFilter {
	private DiskSelectionFilter _diskSelection;

	public DiskFilter(DiskSelectionFilter diskSelection, Properties p, Integer i) {
		_diskSelection = diskSelection;
	}

	protected ArrayList<AssignParser> _assignList;
	
	/**
	 * This method is called to process the ScoreChart of each file.
	 * 
	 * @param sc
	 * @param path
	 */
	public abstract void process(ScoreChart sc, String path);

	public DiskSelectionFilter getDiskSelection() {
		return _diskSelection;
	}
	
	/**
	 * @return ArrayList with 'Root' objects
	 */
	public ArrayList<Root> getRootList() {
		return getDiskSelection().getRootCollection().getRootList();
	}

	public static float parseMultiplier(String string) {
		if (string.equalsIgnoreCase("remove")) {
			return 0;
		}

		boolean isMultiplier;
		float multiplier = 1;

		while (string.length() != 0) {
			char c = string.charAt(0);

			if (c == '*') {
				isMultiplier = true;
				string = string.substring(1);
			} else if (c == '/') {
				isMultiplier = false;
				string = string.substring(1);
			} else {
				isMultiplier = true;
			}

			int pos = string.indexOf('*');

			if (pos == -1) {
				pos = string.length();
			}

			int tmp = string.indexOf('/');

			if ((tmp != -1) && (tmp < pos)) {
				pos = tmp;
			}

			if (isMultiplier) {
				multiplier *= Float.parseFloat(string.substring(0, pos));
			} else {
				multiplier /= Float.parseFloat(string.substring(0, pos));
			}

			string = string.substring(pos);
		}

		return multiplier;
	}
	
	public String getAssignList() {
		StringBuilder sb = new StringBuilder();
		for (int i = 0; i < _assignList.size(); i++) {
			AssignParser ap = _assignList.get(i);
			sb.append(ap.getRoot());
			
			if (i+1 != _assignList.size()) {
				sb.append(',');
			}
		}
		return sb.toString();
	}
}
