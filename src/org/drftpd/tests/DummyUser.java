package org.drftpd.tests;

import net.sf.drftpd.event.listeners.Trial;

import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;


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
        _created = l;
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

    //    public void setUploadedBytesForTrialPeriod(int period, long l) {
    //        switch (period) {
    //        case Trial.PERIOD_DAILY:
    //            setUploadedBytesDay(l);
    //
    //            return;
    //
    //        case Trial.PERIOD_MONTHLY:
    //            setUploadedBytesMonth(l);
    //
    //            return;
    //
    //        case Trial.PERIOD_WEEKLY:
    //            setUploadedBytesWeek(l);
    //
    //            return;
    //
    //        default:
    //            throw new RuntimeException();
    //        }
    //    }
    public void setUploadedBytesWeek(long bytes) {
        _uploadedBytes[P_WEEK] = bytes;
    }

    public UserManager getUserManager() {
        return _userManager;
    }
}
