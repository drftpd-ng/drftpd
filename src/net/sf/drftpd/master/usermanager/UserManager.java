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
package net.sf.drftpd.master.usermanager;
import java.util.Collection;
import java.util.List;

import net.sf.drftpd.master.ConnectionManager;

/**
 * This is the base class of all the user manager classes.
 * If we want to add a new user manager, we have to override
 * this class.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @version $Id: UserManager.java,v 1.20 2004/02/10 00:03:09 mog Exp $
 */
public interface UserManager {

	/**
	 * A kind of constuctor defined in the interface for allowing the usermanager
	 * to get a hold of the ConnectionManager object for dispatching events etc.
	 */
	public abstract void init(ConnectionManager mgr);
	public abstract User create(String username) throws UserFileException;
	/**
	 * User existance check.
	 *
	 * @param name user name
	 */
	public abstract boolean exists(String name);
	public abstract Collection getAllGroups() throws UserFileException;

	/**
	 * Get all user names in the system.
	 */
	public abstract List getAllUsers() throws UserFileException;
	public abstract Collection getAllUsersByGroup(String group)
		throws UserFileException;
	

	/**
	 * Get user by name.
	 */
	//TODO garbage collected Map of users.
	public abstract User getUserByName(String name)
		throws NoSuchUserException, UserFileException;
	public User getUserByNameUnchecked(String username) throws NoSuchUserException, UserFileException;
	public abstract void saveAll() throws UserFileException;

}
