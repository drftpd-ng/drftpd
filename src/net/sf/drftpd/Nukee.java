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
package net.sf.drftpd;


/**
 * @author mog
 * @version $Id: Nukee.java,v 1.6 2004/02/10 00:03:04 mog Exp $
 */
public class Nukee implements Comparable {
	private String _username;
	private long _amount;

	public long getBytes() {
		return _amount;
	}

	public int compareTo(Object o) {
		return compareTo((Nukee) o);
	}

	public int compareTo(Nukee o) {
		long thisVal = getBytes();
		long anotherVal = o.getBytes();
		return (
			thisVal < anotherVal ? 1 : (thisVal == anotherVal ? 0 : -1));
	}

	public Nukee(String user, long amount) {
		_username = user;
		_amount = amount;
	}

	public String getUsername() {
		return _username;
	}

	/**
	 * Returns the amount nuked without multiplier.
	 * @return the amount nuked without multiplier.
	 */
	public long getAmount() {
		return _amount;
	}

}
