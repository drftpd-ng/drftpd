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
import java.io.IOException;

import org.drftpd.commands.UserManagement;
import org.drftpd.io.SafeFileOutputStream;
import org.drftpd.master.CommitManager;
import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserManager;
import org.drftpd.util.CommonPluginUtils;

/**
 * @author mog
 * @version $Id$
 */
public class BeanUser extends AbstractUser {

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
		return password.equals(_password);
	}

	public void commit() {
		CommitManager.getCommitManager().add(this);
	}

	public void purge() {
		_purged = true;
		_um.delete(getName());
	}

	public String getPassword() {
		return _password;
	}

	public void setPassword(String password) {
		_password = password;
	}

	public void setUserManager(BeanUserManager manager) {
		_um = manager;
	}

	/**
	 * Setter for userfile backwards comptibility. Should work but i had nothing
	 * to test with.
	 */
	// public void setGroupSlots(int s) {
	// getKeyedMap().setObject(UserManagement.GROUPSLOTS, s);
	// }
	/**
	 * Setter for userfile backwards comptibility. Should work but i had nothing
	 * to test with.
	 */
	public void setGroupLeechSlots(int s) {
		getKeyedMap().setObject(UserManagement.LEECHSLOTS, s);
	}

	public void writeToDisk() throws IOException {
		if (_purged)
			return;
		XMLEncoder out = null;
		try {
			out = _um.getXMLEncoder(new SafeFileOutputStream(_um
					.getUserFile(getName())));
			ClassLoader prevCL = Thread.currentThread().getContextClassLoader();
			Thread.currentThread().setContextClassLoader(CommonPluginUtils.getClassLoaderForObject(this));
			out.writeObject(this);
			Thread.currentThread().setContextClassLoader(prevCL);
		} finally {
			if (out != null)
				out.close();
		}
	}

	public String descriptiveName() {
		return getName();
	}
}
