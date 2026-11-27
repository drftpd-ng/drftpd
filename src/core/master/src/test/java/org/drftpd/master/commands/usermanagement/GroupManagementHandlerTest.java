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
package org.drftpd.master.commands.usermanagement;

import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.usermanager.Group;
import org.drftpd.master.vfs.DirectoryHandle;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GroupManagementHandlerTest {

    @Test
    public void grprenOfUnknownGroupIs500() throws Exception {
        // a session whose user manager knows no group at all
        BaseFtpConnection conn = new BaseFtpConnection() {
            @Override
            public Group getGroupNull(String group) {
                return null;
            }
        };
        CommandRequest request = new CommandRequest("SITE GRPREN", "nosuchgroup newname",
                new DirectoryHandle("/"), null, conn, new Properties());
        CommandResponse response = new GroupManagementHandler().doSITE_GRPREN(request);
        assertEquals(500, response.getCode());
    }
}
