package org.drftpd.tests;

import net.sf.drftpd.event.listeners.Trial;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.UserFileException;

public class DummyUser extends AbstractUser {
	public DummyUser(String name) {
		super(name, null);
	}

	public DummyUser(String user, DummyUserManager userManager) {
		super(user, userManager);
	}

	public DummyUser(String username, long time) {
		this(username);
		setCreated(time);
	}

	public boolean checkPassword(String password) {
		return true;
	}

	public void commit() throws UserFileException {
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

	public void setLastReset(long l) {
		lastReset = l;
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
	public void setUploadedBytesWeek(long bytes) {
		uploadedBytesWeek = bytes;
	}

}
