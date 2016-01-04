/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.sitebot.plugins.dailystats.event;

import java.util.Collection;

import org.drftpd.plugins.sitebot.plugins.dailystats.UserStats;

/**
 * @author djb61
 * @version $Id: StatsEvent.java 1925 2009-06-15 21:46:05Z tdsoul $
 */
public class StatsEvent {

	private String _type;
	private Collection<UserStats> _outputStats;

	public StatsEvent(String type, Collection<UserStats> outputStats) {
		_type = type;
		_outputStats = outputStats;
	}

	public String getType() {
		return _type;
	}

	public Collection<UserStats> getOutputStats() {
		return _outputStats;
	}
}
