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
import java.beans.ExceptionListener;
import java.beans.XMLDecoder;
import java.beans.XMLEncoder;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.OutputStream;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.HostMask;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * This is a new usermanager that after recommendation from captain- serializes
 * user data using javabeans XMLSerialize.
 * 
 * @author mog
 * @version $Id$
 */
public class BeanUserManager extends AbstractUserManager {

	private String _userpath = "users/javabeans/";

	private File _userpathFile = new File(_userpath);

	protected static final Logger logger = Logger
			.getLogger(BeanUserManager.class);

	public BeanUserManager() throws UserFileException {
		this(true);
	}

	public BeanUserManager(boolean createIfNoUser) throws UserFileException {
		super();
		init(createIfNoUser);
	}

	protected User createUser(String username) {
		return new BeanUser(this, username);
	}

	public User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException {
		XMLDecoder xd = null;
		try {
			BeanUser user = (BeanUser) _users.get(username);

			if (user != null) {
				user.reset(getGlobalContext());
				return user;
			}

			xd = new XMLDecoder(new FileInputStream(getUserFile(username)));
			user = (BeanUser) xd.readObject();

			user.setUserManager(this);
			_users.put(user.getName(), user);
			user.reset(getGlobalContext());
			return user;
		} catch (FileNotFoundException ex) {
			throw new NoSuchUserException("No such user", ex);
		} catch (Exception ex) {
			if (ex instanceof NoSuchUserException) {
				throw (NoSuchUserException) ex;
			}
			throw new UserFileException("Error loading " + username, ex);
		} finally {
			if (xd != null) {
				xd.close();
			}
		}
	}

	public static void main(String args[]) throws UserFileException {
		BeanUserManager bu = new BeanUserManager();
		User u = bu.createUser("drftpd");
		u.commit();
	}

	protected File getUserFile(String username) {
		return new File(_userpath + username + ".xml");
	}

	public XMLEncoder getXMLEncoder(OutputStream out) {
		XMLEncoder e = new XMLEncoder(out);
		e.setExceptionListener(new ExceptionListener() {
			public void exceptionThrown(Exception e1) {
				logger.error("", e1);
			}
		});
		e.setPersistenceDelegate(BeanUser.class,
				new DefaultPersistenceDelegate(new String[] { "name" }));
		e.setPersistenceDelegate(Key.class, new DefaultPersistenceDelegate(
				new String[] { "owner", "key", "type" }));
		e.setPersistenceDelegate(HostMask.class,
				new DefaultPersistenceDelegate(new String[] { "mask" }));
		return e;
	}

	protected File getUserpathFile() {
		return _userpathFile;
	}
}
