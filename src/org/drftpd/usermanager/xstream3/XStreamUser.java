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
package org.drftpd.usermanager.xstream3;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import net.sf.drftpd.util.Crypt;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Logger;

import org.drftpd.usermanager.AbstractUser;
import org.drftpd.usermanager.AbstractUserManager;
import org.drftpd.usermanager.PlainTextPasswordUser;
import org.drftpd.usermanager.UnixPassword;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.UserManager;

import java.io.File;
import java.io.IOException;


/**
 * @author mog
 * @version $Id: XStreamUser.java 776 2004-11-08 18:39:32Z mog $
 */
public class XStreamUser extends AbstractUser implements PlainTextPasswordUser,
    UnixPassword {
    private transient AbstractUserManager _userManager;
    private String _password;
    private String _unixPassword;
    private boolean _purged = false;

    public XStreamUser(XStreamUserManager userManager, String username) {
        super(username);
        _userManager = userManager;
    }

    public boolean checkPassword(String password) {
        if (_password == null) {
            if (_unixPassword == null) {
                throw new IllegalStateException("no password set");
            }

            if (_unixPassword.equals(Crypt.crypt(_unixPassword.substring(0, 2),
                            password))) {
                setPassword(password);

                return true;
            }

            return false;
        }

        return _password.equals(password);
    }

    public void commit() throws UserFileException {
        if (_purged) {
            return;
        }

        try {
            XStream xst = new XStream(new DomDriver());

            if (!(_userManager instanceof XStreamUserManager)) {
                throw new ClassCastException(
                    "XStreamUser without reference to XStreamUserManager");
            }

            SafeFileWriter out = new SafeFileWriter(((XStreamUserManager) _userManager).getUserFile(
                        this.getUsername()));

            try {
                out.write(xst.toXML(this));
            } finally {
                out.close();
            }

            Logger.getLogger(XStreamUser.class).debug("wrote " + getUsername());
        } catch (IOException ex) {
            throw new UserFileException("Error writing userfile for " +
                this.getUsername() + ": " + ex.getMessage(), ex);
        }
    }

    public String getPassword() {
        return _password;
    }

    public String getUnixPassword() {
        return _unixPassword;
    }

    public void purge() {
        _purged = true;
        _userManager.remove(this);

        if (!(_userManager instanceof XStreamUserManager)) {
            throw new ClassCastException(
                "XStreamUser without reference to XStreamUserManager");
        }

        File userfile = ((XStreamUserManager) _userManager).getUserFile(this.getUsername());
        userfile.delete();
    }

    public void setPassword(String password) {
        _unixPassword = null;
        _password = password;
    }

    public void setUnixPassword(String password) {
        _password = null;
        _unixPassword = password;
    }

    public void update() {
        //an update was made, but commit() should be called from all places so
        // we don't need to do anything.
        //if we do, make sure it's implemented in all set and update methods in
        // AbstractUser
    }

    void setUserManager(AbstractUserManager um) {
        _userManager = um;
    }

    public UserManager getUserManager() {
        return _userManager;
    }

    /* (non-Javadoc)
     * @see org.drftpd.usermanager.AbstractUser#getAbstractUserManager()
     */
    public AbstractUserManager getAbstractUserManager() {
        return _userManager;
    }
}
