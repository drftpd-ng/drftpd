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
package net.sf.drftpd.master.usermanager.xstream;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;

import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.util.Crypt;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Logger;
import org.drftpd.usermanager.PlainTextPasswordUser;
import org.drftpd.usermanager.UnixPassword;
import org.drftpd.usermanager.UserExistsException;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

/**
 * @author mog
 * @version $Id: XStreamUser.java,v 1.11 2004/11/06 07:55:33 mog Exp $
 */
public class XStreamUser
	extends AbstractUser
	implements PlainTextPasswordUser, UnixPassword {
	private String unixPassword;
	private String password;
	transient XStreamUserManager _usermanager;
	private transient boolean purged;

	public XStreamUser(XStreamUserManager usermanager, String username) {
		super(username);
		created = System.currentTimeMillis();
		this._usermanager = usermanager;
	}

	public boolean checkPassword(String password) {
		if (this.password == null) {
			if (this.unixPassword == null)
				throw new IllegalStateException("no password set");
			if (this
				.unixPassword
				.equals(
					Crypt.crypt(
						this.unixPassword.substring(0, 2),
						password))) {
				setPassword(password);
				return true;
			}
			return false;
		}
		return this.password.equals(password);
	}

	public void setPassword(String password) {
		this.unixPassword = null;
		this.password = password;
	}

	public String getPassword() {
		return this.password;
	}

	public void rename(String username)
		throws UserExistsException, UserFileException 
	{
		_usermanager.rename(this, username); // throws ObjectExistsException
		_usermanager.getUserFile(this.getUsername()).delete();
		this.username = username;
		commit(); // throws IOException
	}

	public void commit() throws UserFileException {
		if (this.purged)
			return;

		try {
                        XStream xst = new XStream(new DomDriver());
			SafeFileWriter out = new SafeFileWriter(
                            _usermanager.getUserFile(this.getUsername())
                        );
			try {
				out.write(xst.toXML(this));
			} finally {
				out.close();
			}
			Logger.getLogger(XStreamUser.class).debug("wrote "+getUsername());
		} catch (IOException ex) {
			throw new UserFileException(
				"Error writing userfile for "
					+ this.getUsername()
					+ ": "
					+ ex.getMessage(), ex);
		}
	}

	public void purge() {
		this.purged = true;
		_usermanager.remove(this);
		File userfile = _usermanager.getUserFile(this.getUsername());
		userfile.delete();
	}

	protected void finalize() throws Throwable {
		this.commit();
	}

	public void update() {
		//an update was made, but commit() should be called from all places so we don't need to do anything.
		//if we do, make sure it's implemented in all set and update methods in AbstractUser
	}
	public String getUnixPassword() {
		return unixPassword;
	}
	public void setUnixPassword(String password) {
		this.password = null;
		this.unixPassword = password;
	}
	private void readObject(ObjectInputStream stream)
		throws IOException, ClassNotFoundException {
		stream.defaultReadObject();
		if(groups == null) groups = new ArrayList();
		if(ipMasks == null) ipMasks = new ArrayList();
	}

	public UserManager getUserManager() {
		return _usermanager;
	}

	void setUserManager(XStreamUserManager manager) {
		_usermanager = manager;
	}

}
