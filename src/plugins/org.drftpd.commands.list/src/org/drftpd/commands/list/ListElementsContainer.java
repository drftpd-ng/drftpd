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
package org.drftpd.commands.list;

import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.Session;
import org.drftpd.vfs.InodeHandleInterface;

import java.util.ArrayList;

/**
 * @author djb61
 * @version $Id$
 */
public class ListElementsContainer {

	private ArrayList<String> _fileTypes = new ArrayList<>();

	private ArrayList<InodeHandleInterface> _elements =
            new ArrayList<>();

	private int _numOnline;

	private int _numTotal;

	private Session _session;

	private String _user;

	private StandardCommandManager _cManager;

	public ListElementsContainer(Session session, String user, StandardCommandManager cManager) {
		_numOnline = 0;
		_numTotal = 0;
		_session = session;
		_user = user;
		_cManager = cManager;
	}

	public void addFileType(String type) {
		_fileTypes.add(type);
	}

	public ArrayList<InodeHandleInterface> getElements() {
		return _elements;
	}

	public ArrayList<String> getFileTypes() {
		return _fileTypes;
	}

	public int getNumOnline() {
		return _numOnline;
	}

	public int getNumTotal() {
		return _numTotal;
	}

	public Session getSession() {
		return _session;
	}

	public String getUser() {
		return _user;
	}

	public StandardCommandManager getCommandManager() {
		return _cManager;
	}

	public void setNumOnline(int numOnline) {
		_numOnline = numOnline;
	}

	public void setNumTotal(int numTotal) {
		_numTotal = numTotal;
	}
}
