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
package net.sf.drftpd.master.usermanager.xstream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.master.usermanager.UserManager;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
/**
 * @author mog
 * @version $Id: XStreamUserManager.java,v 1.6 2004/07/06 00:55:34 zubov Exp $
 */
public class XStreamUserManager extends UserManager {
	private static final Logger logger = Logger
			.getLogger(XStreamUserManager.class.getName());
	private ConnectionManager _connManager;
	String userpath = "users/xstream/";
	File userpathFile = new File(userpath);
	public XStreamUserManager() throws UserFileException {
		if (!userpathFile.exists() && !userpathFile.mkdirs()) {
			throw new UserFileException(new IOException(
					"Error creating folders: " + userpathFile));
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
			User user = createUser("drftpd");
			user.setGroup("drftpd");
			user.setPassword("drftpd");
			try {
				user.addIPMask("*@127.0.0.1");
				user.addIPMask("*@0:0:0:0:0:0:0:1");
			} catch (DuplicateElementException e) {
			}
			try {
				user.addSecondaryGroup("siteop");
			} catch (DuplicateElementException e1) {
			}
			user.commit();
		}
	}
	public User createUser(String username) {
		XStreamUser user = new XStreamUser(this, username);
		return user;
	}
	public void delete(String username) {
		getUserFile(username).delete();
	}
	public List getAllUsers() throws UserFileException {
		ArrayList users = new ArrayList();
		String userpaths[] = userpathFile.list();
		for (int i = 0; i < userpaths.length; i++) {
			String userpath = userpaths[i];
			if (!userpath.endsWith(".xml"))
				continue;
			String username = userpath.substring(0, userpath.length()
					- ".xml".length());
			try {
				users.add((XStreamUser) getUserByNameUnchecked(username));
				// throws IOException
			} catch (NoSuchUserException e) {
			} // continue
		}
		return users;
	}
	public User getUserByNameUnchecked(String username)
			throws NoSuchUserException, UserFileException {
		try {
			XStreamUser user = (XStreamUser) _users.get(username);
			if (user != null) {
				return user;
			}
			XStream inp = new XStream(new DomDriver());
			FileReader in;
			try {
				in = new FileReader(getUserFile(username));
			} catch (FileNotFoundException ex) {
				throw new NoSuchUserException("No such user");
			}
			try {
				user = (XStreamUser) inp.fromXML(in);
				//throws RuntimeException
				user.setUserManager(this);
				_users.put(user.getUsername(), user);
				user.reset(_connManager);
				return user;
			} catch (Exception e) {
				throw new FatalException(e);
			}
		} catch (Throwable ex) {
			if (ex instanceof NoSuchUserException)
				throw (NoSuchUserException) ex;
			throw new UserFileException("Error loading " + username, ex);
		}
	}
	protected File getUserFile(String username) {
		return new File(userpath + username + ".xml");
	}
	public void saveAll() throws UserFileException {
		logger.log(Level.INFO, "Saving userfiles: " + _users);
		for (Iterator iter = _users.values().iterator(); iter.hasNext();) {
			Object obj = iter.next();
			if (!(obj instanceof XStreamUser))
				throw new ClassCastException("not instanceof XStreamUser");
			XStreamUser user = (XStreamUser) obj;
			user.commit();
		}
	}
}