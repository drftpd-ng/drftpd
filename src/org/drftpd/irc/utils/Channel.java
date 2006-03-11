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

package org.drftpd.irc.utils;

import java.io.UnsupportedEncodingException;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.Blowfish;

import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;

/**
 * Channel configuration.
 * @author fr0w
 */
public class Channel {
	
	String _name = "";
	String _key = "";
	String _blowkey = null;
	String _permissions = "";	
	Blowfish _blowfish = null;	
	boolean _hasOp = false;	
	boolean _isOn = false;

	/**
	 * Creates a Channel instance.
	 * @param name
	 * @param key
	 * @param blowkey
	 * @param perms
	 */
	public Channel(String name, String key, String blowkey, String perms) {
		_name = name;
		_key = key;
		
		if (blowkey == null || blowkey.equals(""))
			_blowkey = null;
		else 
			_blowkey = blowkey;
		
		_permissions = perms;
		if (_blowkey != null) {
			_blowfish = new Blowfish(_blowkey);
		}
	}
	
	/**
	 * @return the channel name.
	 */
	public String getName() {
		return _name;
	}

	/**
	 * @return the channel key without checking permissions.
	 */
	public String getKey() {
		return _key;
	}
	
	/**
	 * @param user
	 * @return the channel key according to the user permissions.
	 * @throws ObjectNotFoundException
	 */
	public String getChannelKey(User user) throws ObjectNotFoundException {
		if (checkPerms(user) && !getKey().equals("")) {
			return getKey();
		}
		throw new ObjectNotFoundException("No Permissions");
	}

	/**
	 * @return the blowfish key (String)
	 */
	public String getBlowKey() {
		return _blowkey;
	}

	/**
	 * @return the blowfish key (Object)
	 * @throws ObjectNotFoundException 
	 */
	public Blowfish getBlowFish() throws ObjectNotFoundException {
		if (getBlowKey() == null)
			throw new ObjectNotFoundException("Blowfish not enabled");
		
		if (_blowfish == null) {
			_blowfish = new Blowfish(_blowkey);
		}
		return _blowfish;
	}
	
	/**
	 * @param user
	 * @return The blowfish key, according to the user permission.
	 * @throws ObjectNotFoundException
	 */
	public String getBlowfishKey(User user) throws ObjectNotFoundException {
		if (checkPerms(user)) {
			if (getBlowKey() == null) {
				throw new ObjectNotFoundException("Blowfish not enabled");
			}
			return getBlowKey();
		}
		throw new ObjectNotFoundException("No Permissions");
	}
	

	/**
	 * Decrypts a message with the current blowfish key.
	 * @param msg
	 * @throws UnsupportedEncodingException
	 */
	public String decrypt(String msg) throws UnsupportedEncodingException {
		try {
			return getBlowFish().Decrypt(msg);
		} catch (StringIndexOutOfBoundsException e) {
			e.printStackTrace();
			throw new UnsupportedEncodingException();
		} catch (ObjectNotFoundException e) {
			// no key was set.
			return msg;
		}
	}

	/**
	 * @param message
	 * @return an encrypted message with the current blowfish key.
	 */
	public String encrypt(String message) {
		try {
			return getBlowFish().Encrypt(message);
		} catch (ObjectNotFoundException e) {
			// no key was set.
			return message;
		}
	}
	
	/**
	 * Set which users have access to this channel.
	 * @param perms
	 */
	public void setPermissions(String perms) {
		_permissions = (perms.equals("")) ? "*" : perms;
	}

	/**
	 * @return the current permissions of this channel.
	 */
	public String getPermissions() {
		return _permissions;
	}

	public boolean checkPerms(User user) {
		Permission p = new Permission(FtpConfig.makeUsers(new StringTokenizer(
				_permissions)));
		return p.check(user);
	}

	/**
	 * @return true if the sitebot has op on this channel.
	 */
	public boolean hasOp() {
		return _hasOp;
	}

	public void setOp(boolean status) {
		_hasOp = status;
	}
	
	/**
	 * @return true if the bot is on that channel.
	 */
	public boolean isOn() {
		return _isOn;
	}
	
	/**
	 * Mark that the bot is on this channel.
	 */
	public void setJoin() {
		_isOn = true;
	}
	
	/**
	 * Mark that the bot left this channel.
	 */
	public void setPart() {
		_isOn = false;
	}
	
	/**
	 * Outputs all info about the channel.<br>
	 * If you want the channel name use: <code>getName()
	 */
	public String toString() {
		return getClass().getName() + "@" + hashCode() + "[name="+getName()+
		",key="+getKey()+",blowfish="+getBlowKey()+
		",isOn="+isOn()+",hasOp="+hasOp()+"]"; 
	}

}
