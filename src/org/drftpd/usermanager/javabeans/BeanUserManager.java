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
import java.io.IOException;
import java.io.OutputStream;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map.Entry;

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

	private static final String _userpath = "users/javabeans/";
	private static final File _userpathFile = new File(_userpath);

	protected static final Logger logger = Logger
			.getLogger(BeanUserManager.class);

	/**
	 * Creates a user named 'username' and adds it to the users map.
	 */
	protected User createUser(String username) {
		BeanUser buser = new BeanUser(this, username);
		_users.put(username, new SoftReference<User>(buser));
		return buser;
	}

	/**
	 * UserManager initializer.
	 * @throws UserFileException
	 */
	public void init() throws UserFileException {
		if (!getUserpathFile().exists() && !getUserpathFile().mkdirs()) {
			throw new UserFileException(new IOException(
					"Error creating directories: " + getUserpathFile()));
		}
		
		_users = new HashMap<String, SoftReference<User>>();
		boolean hasUsers = false; // checking if there is at least 1 existing User.
		
		logger.debug("Creating users map...");
		for (String filename : getUserpathFile().list()) {
			if (filename.endsWith(".xml")) {
				hasUsers = true; // good we have a XML file.
				String username = filename.substring(0, filename.length()-4);
				_users.put(username, null); // the user exists, loading it is useless right now.
			}
		}
		
		if (!hasUsers) {
			createSiteopUser();
		}		
	}
	
	public User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException {

		try {
			SoftReference<User> sf = _users.get(username);

			User user = null;
			if (sf == null || sf.get() == null) {				
				user = loadUser(username);
				
				// this line can be removed later on, debugging purposes only.
				logger.debug("No reference to '"+username+"' found. Was it GC'ed or never loaded?");
				
				_users.put(user.getName(), new SoftReference<User>(user));
			} else {
				return sf.get();
			}
			return user;
		} catch (FileNotFoundException ex) {
			throw new NoSuchUserException("No such user", ex);
		} catch (Exception ex) {
			if (ex instanceof NoSuchUserException) {
				throw (NoSuchUserException) ex;
			}
			throw new UserFileException("Error loading " + username, ex);
		}
	}

	protected User loadUser(String userName) throws FileNotFoundException {
		XMLDecoder xd = null;
		try {
			BeanUser user = null;
			xd = new XMLDecoder(new FileInputStream(getUserFile(userName)));
			logger.debug("Loading '"+userName+"' data from disk.");
			user = (BeanUser) xd.readObject();
			user.setUserManager(this);
			return user;
		} finally {
			if (xd != null)
				xd.close();
		}
	}
	
	/**
	 * List all users.<br>
	 * If some of the User objects are not loaded, they will be loaded and
	 * saved in the memory for future usage, but they still subject to
	 * GarbageColector.
	 */
	public Collection<User> getAllUsers() {
		ArrayList<User> users = new ArrayList<User>(_users.size());
		User u = null;
		for (Entry<String, SoftReference<User>> entry : _users.entrySet()) {
			SoftReference<User> sf = entry.getValue();
			if (sf == null || sf.get() == null) {
				try {
					u = loadUser(entry.getKey());
				} catch (FileNotFoundException e) {
					logger.fatal("This should have happened, but it did. " +
							"Stop deleting users outside DrFTPd!", e);
				}
			} else {
				u = sf.get();
			}
			users.add(u);
			_users.put(entry.getKey(), new SoftReference<User>(u));
		}
		return users;		
	}
	
	public static void main(String args[]) throws UserFileException {
		BeanUserManager bu = new BeanUserManager();
		User u = bu.createUser("drftpd");
		u.commit();
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

	protected final File getUserpathFile() {
		return _userpathFile;
	}	

	protected final File getUserFile(String username) {
		return new File(_userpath + username + ".xml");
	}
}
