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

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.usermanager.AbstractUser;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;
import org.drftpd.master.vfs.CommitManager;

import java.io.File;
import java.io.IOException;

import static org.drftpd.master.util.SerializerUtils.getMapper;

/**
 * @author mog
 * @version $Id$
 */
public class BeanUser extends AbstractUser {

    private static final Logger logger = LogManager.getLogger(BeanUser.class);

    @JsonIgnore
    private transient BeanUserManager _um;

    private int _encryption = 0;
    private String _password = "";

    @JsonIgnore
    private transient boolean _purged;

    public BeanUser() {
        super();
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

    /*
     * Returns current encryption type of password
     */
    public int getEncryption() {
        return _encryption;
    }

    /*
     * Sets encryption type for password
     */
    public void setEncryption(int encryption) {
        _encryption = encryption;
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
        File userFile = _um.getUserFile(getName());
        logger.debug("Wrote userfile for {}", this.getName());
        getMapper().writeValue(userFile, this);
    }

    public String descriptiveName() {
        return getName();
    }
}
