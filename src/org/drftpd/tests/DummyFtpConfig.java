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
package org.drftpd.tests;

import java.io.IOException;
import java.io.StringReader;

import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.util.PortRange;

import org.drftpd.commands.UserManagement;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;

public class DummyFtpConfig extends FtpConfig {
    public DummyFtpConfig() {
        try {
            loadConfig2(new StringReader(""));
            loadConfig1();
            if (_portRange == null) {
                //default portrange if none specified
                _portRange = new PortRange(0);
            }
        } catch (IOException e) {
            throw new RuntimeException();
        }
    }

    public boolean checkPathPermission(String key, User fromUser,
        DirectoryHandle path) {
        return true;
    }

    public float getCreditCheckRatio(DirectoryHandle path,
        User fromUser) {
        return fromUser.getKeyedMap().getObjectFloat(UserManagement.RATIO);
    }

}
