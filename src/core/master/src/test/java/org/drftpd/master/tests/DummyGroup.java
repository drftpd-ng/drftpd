/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.tests;

import org.drftpd.master.commands.usermanagement.UserManagement;
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
        setCreated(new Date(time));
    }

    public boolean checkPassword(String password) {
        return true;
    }

    public void commit() {}

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

    public void writeToDisk() throws IOException { }

    public String descriptiveName() {
        return getName();
    }
}
