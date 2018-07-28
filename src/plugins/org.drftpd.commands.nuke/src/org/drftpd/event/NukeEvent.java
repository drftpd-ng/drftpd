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
package org.drftpd.event;


import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.usermanager.User;

import java.util.Map;

/**
 * @author mog
 * 
 * @version $Id$
 */
public class NukeEvent extends UserEvent {

	private NukeData _nukeData;

	public NukeEvent(User user, String command, long time, NukeData nd) {
		super(user, command, time);
		_nukeData = nd;
	}

	public NukeEvent(User user, String command, NukeData nd) {
		this(user, command, System.currentTimeMillis(), nd);
	}

	public NukeData getNukeData() {
		return _nukeData;
	}

	public int getMultiplier() {
		return getNukeData().getMultiplier();
	}

	public long getNukedAmount() {
		return getNukeData().getAmount();
	}

	public Map<String, Long> getNukees() {
		return getNukeData().getNukees();
	}

	public String getPath() {
		return getNukeData().getPath();
	}

	public String getReason() {
		return getNukeData().getReason();
	}

	public long getSize() {
		return getNukeData().getSize();
	}

	public String toString() {
		return "[NUKE:" + getPath() + ",multiplier=" + getMultiplier() + "]";
	}
}
