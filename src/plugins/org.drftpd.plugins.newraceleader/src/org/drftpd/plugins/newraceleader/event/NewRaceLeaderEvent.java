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
package org.drftpd.plugins.newraceleader.event;

import org.drftpd.util.UploaderPosition;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeaderEvent.java 1925 2009-06-15 21:46:05Z CyBeR $
 */
public class NewRaceLeaderEvent {

	private String _user;
	private String _prevuser;
	private DirectoryHandle _dir;
	private UploaderPosition _uploaderposition;
	private int _files;

	public NewRaceLeaderEvent(String user, String prevuser, DirectoryHandle dir, UploaderPosition uploaderposition, int files) {
		_user = user;
		_prevuser = prevuser;
		_dir = dir;
		_files = files;
		_uploaderposition = uploaderposition;
	}

	public String getUser() {
		return _user;
	}

	public String getPrevUser() {
		return _prevuser;
	}

	public DirectoryHandle getDirectory() {
		return _dir;
	}

	public UploaderPosition getUploaderPosition() {
		return _uploaderposition;
	}

	public int getFiles() {
		return _files;
	}
}
