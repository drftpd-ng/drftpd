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
package net.sf.drftpd.master.usermanager.glftpd;

import net.sf.drftpd.DuplicateElementException;
import net.sf.drftpd.util.Crypt;

import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.UnixPassword;
import org.drftpd.usermanager.UserManager;

import java.util.ArrayList;
import java.util.Collection;


/**
 * @author mog
 * @author zubov
 * @version $Id: GlftpdUser.java,v 1.14 2004/11/05 13:27:21 mog Exp $
 */
public class GlftpdUser extends AbstractUser implements UnixPassword {
    private String _password;
    private ArrayList _privateGroups = new ArrayList();
    private GlftpdUserManager _userManager;

    /**
     * Constructor for GlftpdUser.
     */
    public GlftpdUser(GlftpdUserManager usermanager, String username) {
        super(username);
        _userManager = usermanager;
    }

    public void addPrivateGroup(String group) throws DuplicateElementException {
        addSecondaryGroup(group);
        _privateGroups.add(group);
    }

    public boolean checkPassword(String userPassword) {
        String userhash = Crypt.crypt(this._password.substring(0, 2),
                userPassword);

        if (_password.equals(userhash)) {
            login();

            return true;
        }

        return false;
    }

    public void commit() {
        throw new UnsupportedOperationException();
    }

    /*
     * no such thing in glftpd userfiles
     */
    public long getCreated() {
        return 0;
    }

    public long getLastReset() {
        return System.currentTimeMillis();
    }

    /**
    * Sets the flags.
    * @param flags The flags to set
    */

    //public void setFlags(String flags) {
    //	this.flags = flags;
    //}

    /**
    * Returns the privateGroups.
    * @return Vector
    */
    public Collection getPrivateGroups() {
        return _privateGroups;
    }

    public String getUnixPassword() {
        return _password;
    }

    public void purge() {
        throw new UnsupportedOperationException();
    }

    public void rename(String name) {
        throw new UnsupportedOperationException();
    }

    /**
    * Sets the password.
    * @param password The password to set
    */
    public void setPassword(String password) {
        throw new UnsupportedOperationException();

        //must be encrypted...
        //this.password = password;
    }

    public void setUnixPassword(String password) {
        this._password = password;
    }

    public void update() {
        throw new UnsupportedOperationException();
    }

    public void updateCredits(long credits) {
        throw new UnsupportedOperationException();
    }

    public void updateDownloadedBytes(long bytes) {
        throw new UnsupportedOperationException();
    }

    public void updateUploadedBytes(long bytes) {
        throw new UnsupportedOperationException();
    }

    public UserManager getUserManager() {
        return _userManager;
    }
}
