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
package org.drftpd.master.usermanager.javabeans;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.usermanager.AbstractUser;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;
import org.drftpd.master.vfs.CommitManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

/**
 * @author mog
 * @version $Id$
 */
public class BeanUser extends AbstractUser {

    private static final Logger logger = LogManager.getLogger(BeanUser.class);

    private transient BeanUserManager _um;

    private String _password = "";

    private transient boolean _purged;

    public BeanUser(String username) {
        super(username);
    }

    public BeanUser(BeanUserManager manager, String username) {
        super(username);
        _um = manager;
    }

    public AbstractUserManager getAbstractUserManager() {
        return _um;
    }

    public UserManager getUserManager() {
        return _um;
    }

    public void setUserManager(BeanUserManager manager) {
        _um = manager;
    }

    public boolean checkPassword(String password) {
        return password.equals(_password);
    }

    public void commit() {
        CommitManager.getCommitManager().add(this);
    }

    public void purge() {
        _purged = true;
        _um.deleteUser(getName());
    }

    public String getPassword() {
        return _password;
    }

    public void setPassword(String password) {
        _password = password;
    }

    public void writeToDisk() throws IOException {
        if (_purged) {
            return;
        }
        Gson gson = new GsonBuilder()
                .setDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
                .setPrettyPrinting().create();
        File userFile = _um.getUserFile(getName());
        FileWriter writer = new FileWriter(userFile);
        logger.debug("Wrote userfile for {}", this.getName());
        gson.toJson(this, writer);
        writer.close();
    }

    public String descriptiveName() {
        return getName();
    }
}
