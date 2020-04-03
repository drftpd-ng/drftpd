package org.drftpd.master.tests;

import org.drftpd.commands.UserManagement;
import org.drftpd.master.usermanager.AbstractGroup;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;

import java.io.IOException;
import java.util.Date;


public class DummyGroup extends AbstractGroup {
    private DummyUserManager _userManager;

    public DummyGroup(String name) {
        super(name);
    }

    public DummyGroup(String name, DummyUserManager userManager) {
        super(name);
        _userManager = userManager;
    }

    public DummyGroup(String name, long time) {
        this(name);
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
