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
package net.sf.drftpd.master.usermanager.jsx;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import JSX.ObjIn;

/**
 * @author mog
 * @version $Id: JSXUserManager.java,v 1.29 2004/03/26 00:16:34 mog Exp $
 */
public class JSXUserManager implements UserManager {
	private ConnectionManager _connManager;
	private static final Logger logger =
		Logger.getLogger(JSXUserManager.class.getName());
	String userpath = "users/";
	File userpathFile = new File(userpath);

	Hashtable users = new Hashtable();

	public JSXUserManager() throws UserFileException {

		if (!userpathFile.exists() && !userpathFile.mkdirs()) {
			throw new UserFileException(
				new IOException("Error creating folders: " + userpathFile));
		}

		String userfilenames[] = userpathFile.list();
		int numUsers = 0;
		for (int i = 0; i < userfilenames.length; i++) {
			String string = userfilenames[i];
			if (string.endsWith(".xml")) {
				numUsers++;
			}
		}
		if (numUsers == 0) {
			User user = create("drftpd");
			user.setGroup("drftpd");
			user.setPassword("drftpd");
			user.setRatio(0);
			try {
				user.addIPMask("*@127.0.0.1");
				user.addIPMask("*@0:0:0:0:0:0:0:1");
			} catch (DuplicateElementException e) {
			}
			try {
				user.addGroup("siteop");
			} catch (DuplicateElementException e1) {
			}
			user.commit();
		}
	}

	public User create(String username) throws UserFileException {
		try {
			getUserByName(username);
			//bad
			throw new ObjectExistsException("User already exists");
		} catch (IOException e) {
			//bad
			throw new UserFileException(e);
		} catch (NoSuchUserException e) {
			//good
		}
		JSXUser user = new JSXUser(this, username);
		users.put(user.getUsername(), user);
		return user;
	}

	public boolean exists(String username) {
		return getUserFile(username).exists();
	}

	public Collection getAllGroups() throws UserFileException {
		Collection users = this.getAllUsers();
		ArrayList ret = new ArrayList();

		for (Iterator iter = users.iterator(); iter.hasNext();) {
			User myUser = (User) iter.next();
			Collection myGroups = myUser.getGroups();
			for (Iterator iterator = myGroups.iterator();
				iterator.hasNext();
				) {
				String myGroup = (String) iterator.next();
				if (!ret.contains(myGroup))
					ret.add(myGroup);
			}
		}

		return ret;
	}

	public List getAllUsers() throws UserFileException {
		ArrayList users = new ArrayList();

		String userpaths[] = userpathFile.list();
		for (int i = 0; i < userpaths.length; i++) {
			String userpath = userpaths[i];
			if (!userpath.endsWith(".xml"))
				continue;
			String username =
				userpath.substring(0, userpath.length() - ".xml".length());
			try {
				users.add((JSXUser) getUserByNameUnchecked(username));
				// throws IOException
			} catch (NoSuchUserException e) {
			} // continue
		}
		return users;
	}

	public Collection getAllUsersByGroup(String group)
		throws UserFileException {
		Collection users = getAllUsers();
		for (Iterator iter = users.iterator(); iter.hasNext();) {
			JSXUser user = (JSXUser) iter.next();
			if (!user.isMemberOf(group))
				iter.remove();
		}
		return users;
	}

	public User getUserByNameUnchecked(String username)
		throws NoSuchUserException, UserFileException {
		try {
			JSXUser user = (JSXUser) users.get(username);
			if (user != null) {
				return user;
			}

			ObjIn in;
			try {
				in = new ObjIn(new FileReader(getUserFile(username)));
			} catch (FileNotFoundException ex) {
				throw new NoSuchUserException("No such user");
			}
			try {
				user = (JSXUser) in.readObject();
				//throws RuntimeException
				user.usermanager = this;
				users.put(user.getUsername(), user);
				user.reset(_connManager);
				return user;
			} catch (ClassNotFoundException e) {
				throw new FatalException(e);
			}
		} catch (Throwable ex) {
			if (ex instanceof NoSuchUserException)
				throw (NoSuchUserException) ex;
			throw new UserFileException("Error loading " + username, ex);
		}
	}

	public User getUserByName(String username)
		throws NoSuchUserException, UserFileException {
		JSXUser user = (JSXUser) getUserByNameUnchecked(username);
		if (user.isDeleted())
			throw new NoSuchUserException(user.getUsername() + " is deleted");
		user.reset(_connManager);
		return user;
	}

	public File getUserFile(String username) {
		return new File(userpath + username + ".xml");
	}

	void remove(JSXUser user) {
		this.users.remove(user.getUsername());
	}

	void rename(JSXUser oldUser, String newUsername)
		throws ObjectExistsException {
		if (users.contains(newUsername))
			throw new ObjectExistsException("user " + newUsername + " exists");
		users.remove(oldUser.getUsername());
		users.put(newUsername, oldUser);
	}

	public void saveAll() throws UserFileException {
		logger.log(Level.INFO, "Saving userfiles");
		for (Iterator iter = users.values().iterator(); iter.hasNext();) {
			Object obj = iter.next();
			assert obj instanceof JSXUser;
			JSXUser user = (JSXUser) obj;
			user.commit();
		}
	}

	public void init(ConnectionManager mgr) {
		_connManager = mgr;
	}
}
