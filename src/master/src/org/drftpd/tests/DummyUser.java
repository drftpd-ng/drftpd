package org.drftpd.tests;

import org.drftpd.commands.UserManagement;
import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.UserManager;

import java.io.IOException;
import java.util.Date;


public class DummyUser extends AbstractUser {
    private DummyUserManager _userManager;

    public DummyUser(String name) {
        super(name);
    }

    public DummyUser(String user, DummyUserManager userManager) {
        super(user);
        _userManager = userManager;
    }

    public DummyUser(String username, long time) {
        this(username);
        getKeyedMap().setObject(UserManagement.CREATED, new Date(time));
    }

    public boolean checkPassword(String password) {
        return true;
    }

    public void commit() {
    }

    public void purge() {
        throw new UnsupportedOperationException();
    }

    public void rename(String username) {
        throw new UnsupportedOperationException();
    }

    public void setLastReset(long l) {
        _lastReset = l;
    }

    public void setPassword(String password) {
    }

    public void setUploadedBytes(long bytes) {
        _uploadedBytes[P_ALL] = bytes;
    }

    public void setUploadedBytesDay(long bytes) {
        _uploadedBytes[P_DAY] = bytes;
    }

    public void setUploadedBytesMonth(long bytes) {
        _uploadedBytes[P_MONTH] = bytes;
    }

    public void setUploadedBytesWeek(long bytes) {
        _uploadedBytes[P_WEEK] = bytes;
    }

    public UserManager getUserManager() {
        return _userManager;
    }

    public AbstractUserManager getAbstractUserManager() {
        return _userManager;
    }
    public void writeToDisk() throws IOException {

    }
    public String descriptiveName() {
		return getName();
	}
}
