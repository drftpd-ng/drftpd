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
package org.drftpd.usermanager.javabeans;

import java.beans.DefaultPersistenceDelegate;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * This is a new usermanager that after recommendation from captain-
 * serializes user data using javabeans XMLSerialize.
 * 
 * @author mog
 * @version $Id$
 */
public class BeanUserManager extends AbstractUserManager {

	public BeanUserManager() throws UserFileException {
		if (!_userpathFile.exists() && !_userpathFile.mkdirs()) {
			throw new UserFileException(new IOException(
					"Error creating folders: " + _userpathFile));
		}
	}

	private String _userpath = "users/javabeans0/";

	private File _userpathFile = new File(_userpath);

	protected User createUser(String username) {
		return new BeanUser(this, username);
	}

	protected void delete(String string) {
	}

	public Collection getAllUsers() throws UserFileException {
		return null;
	}

	public User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException {
		return null;
	}

	public void saveAll() throws UserFileException {
	}

	public static void main(String args[]) throws UserFileException {
		BeanUserManager bu = new BeanUserManager();
		User u = bu.createUser("drftpd");
		u.commit();
	}

	File getUserFile(String username) {
		return new File(_userpath = "users/javabeans0/" + username + ".xml");
	}

	public XMLEncoder getXMLEncoder(OutputStream out) {
		XMLEncoder e = new XMLEncoder(out);
		e.setPersistenceDelegate(BeanUser.class,
				new DefaultPersistenceDelegate(new String[] { "name" }));
		return e;
	}
}
