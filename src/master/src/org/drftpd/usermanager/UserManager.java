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
package org.drftpd.usermanager;

import org.drftpd.master.cron.TimeEventInterface;

import java.util.Collection;

/**
 * @author mog
 * @version $Id$
 */
public interface UserManager extends TimeEventInterface {
	void init() throws UserFileException;
	
	User create(String username) throws UserFileException;

	Collection<String> getAllGroups();

	/**
	 * Get all user names in the system.
	 */
    Collection<User> getAllUsers();

	Collection<User> getAllUsersByGroup(String group);

	/**
	 * Get user by name.
	 */
    User getUserByName(String username)
			throws NoSuchUserException, UserFileException;

	User getUserByIdent(String ident, String botName)
			throws NoSuchUserException;

	User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException;

	User getUserByNameIncludeDeleted(String argument)
			throws NoSuchUserException, UserFileException;
}
