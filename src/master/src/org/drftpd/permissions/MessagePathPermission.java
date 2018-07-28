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
package org.drftpd.permissions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author mog
 * 
 * @version $Id$
 */
public class MessagePathPermission extends StringPathPermission {
	private ArrayList<String> _message;

	public MessagePathPermission(String pattern, String messageFile,
			Collection<String> users) throws IOException {
		super(pattern, users);
		
		_message = new ArrayList<>();

		BufferedReader in = new BufferedReader(new FileReader(messageFile));
		String line;

		try {
			while ((line = in.readLine()) != null) {
				_message.add(line);
			}
		} finally {
			in.close();
		}

		_message.trimToSize();
	}
	
	public ArrayList<String> getMessage() {
		return _message;
	}
}
