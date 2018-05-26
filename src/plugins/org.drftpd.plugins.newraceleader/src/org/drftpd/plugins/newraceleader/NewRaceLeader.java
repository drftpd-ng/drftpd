/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.plugins.newraceleader;

import org.drftpd.GlobalContext;
import org.drftpd.plugins.newraceleader.event.NewRaceLeaderEvent;
import org.drftpd.util.UploaderPosition;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;

import java.util.Collection;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeader.java 2393 2011-04-11 20:47:51Z cyber1331 $
 */
public class NewRaceLeader {
	private DirectoryHandle _dir;
	private long _time;
	private String _winner;

	public NewRaceLeader(FileHandle file,Collection<UploaderPosition> uploaderposition, String nick) {
		_dir = file.getParent();
		_time = System.currentTimeMillis();

		if (uploaderposition.iterator().hasNext()) {
			_winner = uploaderposition.iterator().next().getUsername();
		} else {
			_winner = nick;
		}

	}

	public DirectoryHandle getDir() {
		return _dir;
	}

	public long getTime() {
		return _time;
	}

	public String getWinner() {
		return _winner;
	}

	public void check(String user, int missing, int files, Collection<UploaderPosition> racers) {
		UploaderPosition uploaderposition = racers.iterator().next();
		if ((uploaderposition.getUsername().equals(user)) && (!user.equals(_winner))) {

			// Ignore on halfway as we already announce leader during this point
			int halfway = (int) Math.floor((double) files / 2);
			if (missing != halfway) {
				GlobalContext.getEventService().publishAsync(new NewRaceLeaderEvent(user,_winner,getDir(),uploaderposition, missing));
			}

			_winner = user;
		}
	}
}
