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

import java.beans.XMLEncoder;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.Serializable;

import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;

/**
 * @author mog
 * @version $Id$
 */
public class BeanUser extends AbstractUser implements Serializable {

	private BeanUserManager _um;
	private String _password = "";
	private boolean _purged;

	public BeanUser(String username) {
		super(username);
	}

	public BeanUser(BeanUserManager manager, String username) {
		super(username);
		_um = manager;
	}

	public AbstractUserManager getAbstractUserManager() {
		return _um;
	}

	public UserManager getUserManager() {
		return _um;
	}

	public boolean checkPassword(String password) {
		return password.equals(this._password);
	}

	public void commit() throws UserFileException {
		if(_purged) return;
		try {
			new XMLEncoder(new FileOutputStream(_um.getUserFile(getName()))).writeObject(this);
		} catch (FileNotFoundException e) {
			throw new UserFileException(e);
		}
	}

	public void purge() {
		_purged = true;
		_um.getUserFile(getName()).delete();
	}

	public String getPassword() {
		return this._password;
	}

	public void setPassword(String password) {
		this._password = password;
	}

}
