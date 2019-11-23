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

import com.cedarsoftware.util.io.JsonReader;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.CommonPluginUtils;

import java.beans.XMLDecoder;
import java.io.*;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;

/**
 * This is a new usermanager that after recommendation from captain- serializes
 * user data using javabeans XMLSerialize.
 * 
 * @author mog
 * @version $Id$
 */
public class BeanUserManager extends AbstractUserManager {

	private static final String _userpath = "userdata/users/javabeans/";
	private static final File _userpathFile = new File(_userpath);

	protected static final Logger logger = LogManager.getLogger(BeanUserManager.class);

	/**
	 * Creates a user named 'username' and adds it to the users map.
	 */
	protected synchronized User createUser(String username) {
		BeanUser buser = new BeanUser(this, username);
		_users.put(username, new SoftReference<>(buser));
		return buser;
	}

	/**
	 * UserManager initializer.
	 * @throws UserFileException
	 */
	public void init() throws UserFileException {
		super.init();
		if (!getUserpathFile().exists() && !getUserpathFile().mkdirs()) {
			throw new UserFileException(new IOException(
					"Error creating directories: " + getUserpathFile()));
		}
		
		_users = new HashMap<>();
		
		logger.debug("Creating users map...");
		for (String filename : getUserpathFile().list()) {
			if (filename.endsWith(".xml") || filename.endsWith(".json")) {
				String username = filename.substring(0, filename.lastIndexOf('.'));
				_users.put(username, null); // the user exists, loading it is useless right now.
			}
		}
		
		if (_users.size() == 0) {
			createSiteopUser();
		}
	}
	
	/**
	 * Tries to find a user in the Map that matches the 'username'.
	 * This method does not care about if the user exists or not,
	 * it simply tries to find it.
	 * @throws NoSuchUserException, if there's no such user w/ this Username.
	 * @throws UserFileException, if an error (i/o) occured while loading data.
	 */
	public User getUserByNameUnchecked(String username)
	throws NoSuchUserException, UserFileException {
		try {
			return getUserFromSoftReference(username);
		} catch (Exception ex) {
			if (ex instanceof NoSuchUserException) {
				throw (NoSuchUserException) ex;
			}
			throw new UserFileException("Error loading " + username, ex);
		}
	}

	/**
	 * Lowest level method for loading a User object.
	 * @param userName
	 * @throws NoSuchUserException, if there's no such user w/ this Username.
	 * Meaning that the userfile does not exists.
	 * @throws UserFileException, if an error (i/o) occured while loading data.
	 */
	protected User loadUser(String userName) throws NoSuchUserException, UserFileException {
		try (InputStream in = new FileInputStream(getUserFile(userName));
			 JsonReader reader = new JsonReader(in)) {
            logger.debug("Loading '{}' Json data from disk.", userName);
			BeanUser user = (BeanUser) reader.readObject();
			user.setUserManager(this);
			return user;
		} catch (FileNotFoundException e) {
			// Lets see if there is a legacy xml user file to load
			return loadXMLUser(userName);
		} catch (Exception e) {
			throw new UserFileException("Error loading " + userName, e);
		}
	}

	/**
	 * Legacy XML loader used to convert users to json schema.
	 * @param userName
	 * @throws NoSuchUserException, if there's no such user w/ this Username.
	 * Meaning that the userfile does not exists.
	 * @throws UserFileException, if an error (i/o) occured while loading data.
	 */
	private User loadXMLUser(String userName) throws NoSuchUserException, UserFileException {
		File xmlUserFile = getXMLUserFile(userName);
		try (XMLDecoder xd = new XMLDecoder(new BufferedInputStream(new FileInputStream(xmlUserFile)))) {
			BeanUser user;
            logger.debug("Loading '{}' XML data from disk.", userName);
			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			user = (BeanUser) xd.readObject();
			Thread.currentThread().setContextClassLoader(prevCL);
			user.setUserManager(this);
			// Commit new json userfile and delete old xml
			user.commit();
			if (!xmlUserFile.delete()) {
                logger.error("Failed to delete old xml userfile: {}", xmlUserFile.getName());
			}
			return user;
		} catch (FileNotFoundException e) {
			throw new NoSuchUserException("No such user: '"+userName+"'", e);
		} catch (Exception e) {
			throw new UserFileException("Error loading " + userName, e);
		}
	}
	
	/**
	 * This methods fetches the SoftReference from the users map
	 * and checks if it still holds a reference to a User object.<br>
	 * If it does return the object, if not it tries to load the
	 * User data from the disk and return it.
	 * @param name, the username.
	 * @return a User object.
	 * @throws NoSuchUserException, if not such file containing use data was found,
	 * so the user does not exist.
	 * @throws UserFileException, if an error (i/o) occurs during the load. 
	 */
	private synchronized User getUserFromSoftReference(String name)
			throws NoSuchUserException, UserFileException {
		if (!_users.keySet().contains(name)) {
			throw new NoSuchUserException("No such user found: " + name);
		}
		SoftReference<User> sf = _users.get(name);
		User u = null;
		if (sf != null) {
			u = sf.get();
		}
		if (u == null) {
			// user object was garbage collected or was never loaded
			u = loadUser(name);
			_users.put(name, new SoftReference<>(u));
		}
		return u;
	}
	
	/**
	 * List all users.<br>
	 * If some of the User objects are not loaded, they will be loaded and
	 * saved in the memory for future usage, but they still subject to
	 * GarbageColector.
	 */
	public synchronized Collection<User> getAllUsers() {
		ArrayList<User> users = new ArrayList<>(_users.size());
		for (Iterator<String> iter = _users.keySet().iterator(); iter.hasNext();) {
			String name = iter.next();
			try {
				User u = getUserFromSoftReference(name);
				users.add(u);
				_users.put(name, new SoftReference<>(u));
			} catch (NoSuchUserException e) {
                logger.error("{} data wasnt found in the disk! How come the user is in the Map and does not have a userfile?! Deleting it.", name);
				iter.remove();
				// nothing else to do, user wasnt loaded properly.
			} catch (UserFileException e) {
                logger.error("Error loading {}", name, e);
				// nothing else to do, an error ocurred while loading data.
			}
		}
		return users;		
	}
	
	/**
	 * Testing routine.
	 * @param args
	 * @throws UserFileException
	 */
	public static void main(String args[]) throws UserFileException {
		BeanUserManager bu = new BeanUserManager();
		User u = bu.createUser("drftpd");
		u.commit();
	}

	protected final File getUserpathFile() {
		return _userpathFile;
	}	

	protected final File getUserFile(String username) {
		return new File(_userpath + username + ".json");
	}

	private File getXMLUserFile(String username) {
		return new File(_userpath + username + ".xml");
	}
}
