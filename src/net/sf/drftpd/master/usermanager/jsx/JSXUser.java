/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package net.sf.drftpd.master.usermanager.jsx;

import JSX.ObjOut;

import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.PlainTextPasswordUser;
import net.sf.drftpd.master.usermanager.UnixPassword;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.util.Crypt;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.io.ObjectInputStream;

import java.util.ArrayList;


/**
 * @author mog
 * @version $Id: JSXUser.java,v 1.19 2004/11/02 07:32:43 zubov Exp $
 */
public class JSXUser extends AbstractUser implements PlainTextPasswordUser,
    UnixPassword {
    private String password;
    private String unixPassword;

    public JSXUser(JSXUserManager usermanager, String username) {
        super(username, usermanager);
    }

    public boolean checkPassword(String password) {
        if (this.password == null) {
            if (this.unixPassword == null) {
                throw new IllegalStateException("no password set");
            }

            if (this.unixPassword.equals(Crypt.crypt(
                            this.unixPassword.substring(0, 2), password))) {
                setPassword(password);

                return true;
            }

            return false;
        }

        return this.password.equals(password);
    }

    public void commit() throws UserFileException {
        if (_purged) {
            return;
        }

        try {
            if (!(_usermanager instanceof JSXUserManager)) {
                throw new ClassCastException(
                    "JSXUser without reference to JSXUserManager");
            }

            ObjOut out = new ObjOut(new SafeFileWriter(((JSXUserManager) _usermanager).getUserFile(
                            this.getUsername())));

            try {
                out.writeObject(this);
            } finally {
                out.close();
            }

            Logger.getLogger(JSXUser.class).debug("wrote " + getUsername());
        } catch (IOException ex) {
            throw new UserFileException("Error writing userfile for " +
                this.getUsername() + ": " + ex.getMessage(), ex);
        }
    }

    protected void finalize() throws Throwable {
        commit();
    }

    public String getPassword() {
        return this.password;
    }

    public String getUnixPassword() {
        return unixPassword;
    }

    public void purge() {
        _purged = true;
        _usermanager.remove(this);

        if (!(_usermanager instanceof JSXUserManager)) {
            throw new ClassCastException(
                "JSXUser without reference to JSXUserManager");
        }

        File userfile = ((JSXUserManager) _usermanager).getUserFile(this.getUsername());
        userfile.delete();
    }

    private void readObject(ObjectInputStream stream)
        throws IOException, ClassNotFoundException {
        stream.defaultReadObject();

        if (groups == null) {
            groups = new ArrayList();
        }

        if (ipMasks == null) {
            ipMasks = new ArrayList();
        }

        if (tagline == null) {
            tagline = "";
        }
    }

    public void setPassword(String password) {
        this.unixPassword = null;
        this.password = password;
    }

    public void setUnixPassword(String password) {
        this.password = null;
        this.unixPassword = password;
    }

    public void update() {
        //an update was made, but commit() should be called from all places so
        // we don't need to do anything.
        //if we do, make sure it's implemented in all set and update methods in
        // AbstractUser
    }
}
