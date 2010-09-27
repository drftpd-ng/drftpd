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
package org.drftpd.commands.nuke;

import org.drftpd.usermanager.User;

import java.util.Map;

/**
 * Stores data of a nuke.
 * @author fr0w
 * @version $Id$
 */
public class NukeData {

	private String _user;

	private String _path;

	private String _reason;

	private Map<User, Long> _nukees;

	private int _multiplier;

	private long _amount;

	private long _size;

	private long _time;

	/**
	 * Main constructor.
	 * 
	 * @param user,
	 *            Username.
	 * @param path,
	 *            Path of the dir TO BE nuked.
	 * @param reason,
	 *            Reason of the nuke/unnuke.
	 * @param nukees,
	 *            Map of the nuked users.
	 * @param multiplier
	 * @param amount,
	 *            The total amount of nuked bytes.
	 * @param size,
	 *            Size of the nuked dir.
	 * @param time,
	 *            Date of the nuke.
	 */
	public NukeData(String user, String path, String reason,
			Map<User, Long> nukees, int multiplier, long amount, long size,
			long time) {
		_user = user;
		_path = path;
		_reason = reason;
		_nukees = nukees;
		_multiplier = multiplier;
		_amount = amount;
		_size = size;
		_time = time;
	}

	/**
	 * This constructor does not need the TIME variable. Simply add the current
	 * time and try to construct the object with the
	 * 
	 * <pre>
	 * Main Constructor
	 * </pre>.
	 * 
	 * @param user
	 * @param path
	 * @param reason
	 * @param nukees
	 * @param multiplier
	 * @param amount
	 * @param size
	 */
	public NukeData(String user, String path, String reason,
			Map<User, Long> nukees, int multiplier, long amount, long size) {
		this(user, path, reason, nukees, multiplier, amount, size, System
				.currentTimeMillis());
	}

	/**
	 * @return the username of who issued the nuke/unnuke command.
	 */
	public String getUser() {
		return _user;
	}

	/**
	 * Modifies the user name of who issued the nuke/unnuke command.
	 * 
	 * @param user
	 */
	public void setUser(String user) {
		_user = user;
	}

	/**
	 * @return the nuked path.
	 */
	public String getPath() {
		return _path;
	}

	/**
	 * Modifies the nuked path.
	 * 
	 * @param path
	 */
	public void setPath(String path) {
		_path = path;
	}

	/**
	 * @return the Nuke multiplier.
	 */
	public int getMultiplier() {
		return _multiplier;
	}

	/**
	 * Modifies the Nuke multiplier.
	 * 
	 * @param multiplier
	 */
	public void setMultiplier(int multiplier) {
		_multiplier = multiplier;
	}

	/**
	 * @return the amount of nuked bytes.
	 */
	public long getAmount() {
		return _amount;
	}

	/**
	 * Modifies the amount of nuked bytes.
	 * 
	 * @param amount
	 */
	public void setAmount(long amount) {
		_amount = amount;
	}

	/**
	 * @return the nuke/unnuke reason.
	 */
	public String getReason() {
		return _reason;
	}

	/**
	 * Modifies the reason of the Nuke.
	 * 
	 * @param reason
	 */
	public void setReason(String reason) {
		_reason = reason;
	}

	/**
	 * @return the size of the nuked dir.
	 */
	public long getSize() {
		return _size;
	}

	/**
	 * Modifies the size of the nuked dir.
	 * 
	 * @param size
	 */
	public void setSize(long size) {
		_size = size;
	}

	/**
	 * @return the Map of the nuked users.
	 */
	public Map<User, Long> getNukees() {
		return _nukees;
	}

	/**
	 * Modifies the current Map of nuked users.
	 * 
	 * @param map
	 */
	public void setNukees(Map<User, Long> map) {
		_nukees = map;
	}

	/**
	 * @return the date of the nuke.
	 */
	public long getTime() {
		return _time;
	}

	/**
	 * Edit the date of nuke.
	 * 
	 * @param time
	 */
	public void setTime(long time) {
		_time = time;
	}

	/**
	 * [NUKE] - Path: /APPS/MICROSOFT.SUCKS - Multiplier: 3 - UsersNuked: 10 -
	 * Size: 700MB - Reason: no.thx.
	 */
	public String toString() {
		return "[NUKE] - Path: " + getPath() + " - Multiplier: "
				+ getMultiplier() + " - UsersNuked: " + getNukees().size()
				+ " - Size: " + getSize() + " - Reason: " + getReason();
	}

}