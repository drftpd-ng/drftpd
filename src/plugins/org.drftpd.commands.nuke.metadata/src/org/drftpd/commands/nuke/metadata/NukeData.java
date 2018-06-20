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
package org.drftpd.commands.nuke.metadata;

import org.drftpd.dynamicdata.Key;

import java.io.Serializable;
import java.util.Map;

/**
 * @author scitz0
 * @author fr0w
 * @version $id$
 */
@SuppressWarnings("serial")
public class NukeData implements Serializable {

    public static final Key<NukeData> NUKEDATA = new Key<>(NukeData.class, "nuke");

    private String _user;

	private String _path;

	private String _reason;

	private Map<String, Long> _nukees;

	private int _multiplier;

	private long _amount;

	private long _size;

	private long _time;

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
	public Map<String, Long> getNukees() {
		return _nukees;
	}

	/**
	 * Modifies the current Map of nuked users.
	 *
	 * @param map
	 */
	public void setNukees(Map<String, Long> map) {
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

}
