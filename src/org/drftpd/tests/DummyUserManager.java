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
package org.drftpd.tests;

import java.util.Collection;
import java.util.List;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;

/**
 * @author mog
 * @version $Id: DummyUserManager.java,v 1.1 2004/05/16 18:07:31 mog Exp $
 */
public class DummyUserManager implements UserManager {

	private User _user;

	public DummyUserManager() {
		super();
	}

	public void init(ConnectionManager mgr) {
		throw new UnsupportedOperationException();
	}

	public User create(String username) throws UserFileException {
		throw new UnsupportedOperationException();
	}

	public boolean exists(String name) {
		throw new UnsupportedOperationException();
	}

	public Collection getAllGroups() throws UserFileException {
		throw new UnsupportedOperationException();
	}

	public List getAllUsers() throws UserFileException {
		throw new UnsupportedOperationException();

	}

	public Collection getAllUsersByGroup(String group)
		throws UserFileException {
		throw new UnsupportedOperationException();

	}

	public User getUserByName(String name)
		throws NoSuchUserException, UserFileException {
		return _user;
	}
	public void setUser(User user) {
		_user = user;
	}
	public User getUserByNameUnchecked(String username)
		throws NoSuchUserException, UserFileException {
		throw new UnsupportedOperationException();
	}

	public void saveAll() throws UserFileException {
		throw new UnsupportedOperationException();
	}
}
