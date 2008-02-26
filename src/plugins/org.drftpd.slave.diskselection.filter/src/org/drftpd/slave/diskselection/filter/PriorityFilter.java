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

import org.drftpd.PropertyHelper;

/**
 * This filter works this way (may look foolish)
 * 
 * <pre>
 *  x.filter=priority
 *  x.assign=1+10 2+5
 * </pre>
 * 
 * This means that slave.root.1 will have more chances to receive a file then
 * slave.root.2
 * 
 * @author fr0w
 * @version $Id$
 */

public class PriorityFilter extends DiskFilter {

	public PriorityFilter(DiskSelectionFilter diskSelection, Properties p, Integer i) {
		super(diskSelection, p, i);
		_assignList = AssignRoot.parseAssign(this, PropertyHelper.getProperty(p, i+ ".assign"));
	}

	public void process(ScoreChart sc, String path) {
		AssignRoot.addScoresToChart(this, _assignList, sc);
	}
}
