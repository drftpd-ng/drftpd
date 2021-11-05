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
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.usermanager.AbstractGroup;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;
import org.drftpd.master.vfs.CommitManager;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

import static org.drftpd.common.util.SerializerUtils.getSerializer;

/**
 * @author mikevg
 * @version $Id$
 */
public class BeanGroup extends AbstractGroup {

    private static final Logger logger = LogManager.getLogger(BeanGroup.class);

    private transient BeanUserManager _um;

    private transient boolean _purged;

    public BeanGroup(String groupname) {
        super(groupname);
    }

    public BeanGroup(BeanUserManager manager, String groupname) {
        super(groupname);
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

    public void commit() {
        CommitManager.getCommitManager().add(this);
    }

    public void purge() {
        _purged = true;
        _um.deleteGroup(getName());
    }

    public void writeToDisk() throws IOException {
        if (_purged) {
            return;
        }
        Gson gson = getSerializer();
        File groupFile = _um.getGroupFile(getName());
        FileWriter writer = new FileWriter(groupFile);
        logger.debug("Wrote groupfile for {}", this.getName());
        gson.toJson(this, writer);
        writer.close();
    }

    public String descriptiveName() {
        return getName();
    }
}
