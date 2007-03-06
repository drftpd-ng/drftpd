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
package org.drftpd.commandmanager;

import java.io.BufferedReader;
import java.io.IOException;
import java.util.Vector;

import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class CommandResponse extends KeyedMap implements CommandResponseInterface {

	public static final Key CODE = new Key(CommandResponse.class, "code",
			Integer.class);

	public static final Key COMMENT = new Key(CommandResponse.class, "comment",
			Vector.class);

	public static final Key CURRENTDIRECTORY = new Key(CommandResponse.class, "currentDirectory",
			DirectoryHandle.class);

	public static final Key MESSAGE = new Key(CommandResponse.class, "message",
			String.class);

	public static final Key USER = new Key(CommandResponse.class, "user",
			String.class);

	public CommandResponse(int code) {
		setCode(code);
	}

	public CommandResponse(int code, String message) {
		setCode(code);
		setMessage(message);
	}

	public CommandResponse(int code, DirectoryHandle directory, String user) {
		setCode(code);
		setCurrentDirectory(directory);
		setUser(user);
	}

	public CommandResponse(int code, String message, DirectoryHandle directory, String user) {
		setCode(code);
		setMessage(message);
		setCurrentDirectory(directory);
		setUser(user);
	}

	public void addComment(BufferedReader in) throws IOException {
		String line;

		while ((line = in.readLine()) != null) { // throws IOException
			this.addComment(line);
		}
	}

	public void addComment(Object comment) {
		Vector<String> _comments = getComment();
		String resp = String.valueOf(comment);

		if (resp.indexOf('\n') != -1) {
			String[] lines = resp.split("\n");

			for (int i = 0; i < lines.length; i++) {
				_comments.add(lines[i]);
			}
		} else {
			_comments.add(resp);
		}
		setObject(CommandResponse.COMMENT, _comments);
	}

	public void setCode(int code) {
		setObject(CommandResponse.CODE, code);
	}

	public void setCurrentDirectory(DirectoryHandle currentDirectory) {
		setObject(CommandResponse.CURRENTDIRECTORY, currentDirectory);
	}

	public void setMessage(String message) {
		setObject(CommandResponse.MESSAGE, message);
	}

	public void setUser(String currentUser) {
		if (currentUser != null) {
			setObject(CommandResponse.USER, currentUser);
		}
	}

	public int getCode() {
		return ((Integer) getObject(CommandResponse.CODE, new Integer(500))).intValue();
	}

	public Vector<String> getComment() {
		return (Vector<String>) getObject(CommandResponse.COMMENT, new Vector<String>());
	}

	public DirectoryHandle getCurrentDirectory() {
		return (DirectoryHandle) getObject(CommandResponse.CURRENTDIRECTORY, null);
	}

	public String getMessage() {
		return (String) getObject(CommandResponse.MESSAGE, null);
	}

	public String getUser() {
		return (String) getObject(CommandResponse.USER, null);
	}
}
