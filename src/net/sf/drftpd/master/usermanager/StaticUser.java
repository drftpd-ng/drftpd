package net.sf.drftpd.master.usermanager;

/**
 * Contains static user data, used for testing.
 * 
 * @author mog
 * @version $Id: StaticUser.java,v 1.1 2003/12/07 22:31:45 mog Exp $
 */
public class StaticUser extends AbstractUser {

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
}
