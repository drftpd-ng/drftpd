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
package net.sf.drftpd.master.usermanager.glftpd;

import java.util.Collection;
import java.util.Vector;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.UnixPassword;
import net.sf.drftpd.util.Crypt;

/**
 * @author mog
 * @author zubov
 * @version $Id: GlftpdUser.java,v 1.10 2004/07/01 16:07:50 zubov Exp $
 */
public class GlftpdUser extends AbstractUser implements UnixPassword {
	private String password;
	//private String flags;
	private Vector privateGroups = new Vector();
	//private long weeklyAllotment;
	//private GlftpdUserManager usermanager;
	
	/**
	 * Constructor for GlftpdUser.
	 */
	public GlftpdUser(String username) {
		super(username);
		//this.usermanager = usermanager;
	}
	public void addPrivateGroup(String group)
		throws DuplicateElementException {
		addSecondaryGroup(group);
		privateGroups.add(group);
	}
	public boolean checkPassword(String userPassword) {
		String userhash =
			Crypt.crypt(this.password.substring(0, 2), userPassword);
		if (password.equals(userhash)) {
			login();
			return true;
		}
		return false;
	}
	public void commit() {
		throw new UnsupportedOperationException();
	}
	/* 
	 * no such thing in glftpd userfiles
	 */
	public long getCreated() {
		return 0;
	}

	public long getLastReset() {
		return System.currentTimeMillis();
	}
	/**
	* Sets the flags.
	* @param flags The flags to set
	*/
	//public void setFlags(String flags) {
	//	this.flags = flags;
	//}
	/**
	* Returns the privateGroups.
	* @return Vector
	*/
	public Collection getPrivateGroups() {
		return privateGroups;
	}

	public String getUnixPassword() {
		return password;
	}
	public long getWeeklyAllotment() {
		return weeklyAllotment;
	}
	public void purge() {
		throw new UnsupportedOperationException();
	}
	public void rename(String name) {
		throw new UnsupportedOperationException();
	}
	/**
	* Sets the password.
	* @param password The password to set
	*/
	public void setPassword(String password) {
		throw new UnsupportedOperationException();
		//must be encrypted...
		//this.password = password;
	}

	public void setUnixPassword(String password) {
		this.password = password;
	}
	public void setWeeklyAllotment(long weeklyAllotment) {
		this.weeklyAllotment = weeklyAllotment;
	}
	public void update() {
		throw new UnsupportedOperationException();
	}

	public void updateCredits(long credits) {
		throw new UnsupportedOperationException();
	}
	/**
	 * @see net.sf.drftpd.master.usermanager.User#updateDownloadedBytes(long)
	 */
	public void updateDownloadedBytes(long bytes) {
		throw new UnsupportedOperationException();
	}
	/**
	* @see net.sf.drftpd.master.usermanager.User#updateUploadedBytes(long)
	*/
	public void updateUploadedBytes(long bytes) {
		throw new UnsupportedOperationException();
	}

}