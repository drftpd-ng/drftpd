package org.drftpd.tests;

import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.UserExistsException;
import net.sf.drftpd.master.usermanager.UserFileException;

public class DummyUser extends AbstractUser {

	public DummyUser(String name) {
		super(name);
	}

	public boolean checkPassword(String password) {
		return true;
	}

	public void commit() throws UserFileException {

	}

	public void purge() {
		throw new UnsupportedOperationException();
	}

	public void rename(String username)
		throws UserExistsException, UserFileException {
		throw new UnsupportedOperationException();
	}

	public void setPassword(String password) {
		throw new UnsupportedOperationException();
	}

}
