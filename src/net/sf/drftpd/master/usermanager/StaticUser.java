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
package net.sf.drftpd.master.usermanager;

import net.sf.drftpd.event.listeners.Trial;

/**
 * Contains static user data, used for testing.
 * 
 * @author mog
 * @version $Id: StaticUser.java,v 1.3 2004/02/10 00:03:09 mog Exp $
 */
public class StaticUser extends AbstractUser {

	public StaticUser(String username, long time) {
		this(username);
		setCreated(time);
	}

	public StaticUser(String username) {
		super(username);

	}

	public boolean checkPassword(String password) {
		throw new UnsupportedOperationException();
	}

	public void commit() throws UserFileException {
		throw new UnsupportedOperationException();
	}

	public void purge() {
		throw new UnsupportedOperationException();
	}

	public void rename(String username) {
		throw new UnsupportedOperationException();
	}
	public void setCreated(long l) {
		created = l;
	}

	public void setPassword(String password) {
		throw new UnsupportedOperationException();
	}

	public void setUploadedBytes(long bytes) {
		uploadedBytes = bytes;
	}
	public void setUploadedBytesDay(long bytes) {
		uploadedBytesDay = bytes;
	}
	public void setUploadedBytesMonth(long bytes) {
		uploadedBytesMonth = bytes;
	}
	public void setUploadedBytesWeek(long bytes) {
		uploadedBytesWeek = bytes;
	}

	public void setLastReset(long l) {
		lastReset = l;
	}

	public void setUploadedBytesPeriod(int period, long l) {
		switch (period) {
			case Trial.PERIOD_DAILY :
				setUploadedBytesDay(l);
				return;
			case Trial.PERIOD_MONTHLY :
				setUploadedBytesMonth(l);
				return;
			case Trial.PERIOD_WEEKLY :
				setUploadedBytesWeek(l);
				return;
			default :
				throw new RuntimeException();
		}
	}
}
