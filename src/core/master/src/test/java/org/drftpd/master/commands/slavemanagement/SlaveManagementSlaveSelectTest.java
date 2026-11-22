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
package org.drftpd.master.commands.slavemanagement;

import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.slave.network.Transfer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

public class SlaveManagementSlaveSelectTest {
    @Test
    public void testChainTypesMapToDirection() throws Exception {
        assertEquals(Transfer.TRANSFER_RECEIVING_UPLOAD, SlaveManagement.getDirection("up"));
        assertEquals(Transfer.TRANSFER_RECEIVING_UPLOAD, SlaveManagement.getDirection("jobup"));
        assertEquals(Transfer.TRANSFER_RECEIVING_UPLOAD, SlaveManagement.getDirection("JobUp"));
        assertEquals(Transfer.TRANSFER_SENDING_DOWNLOAD, SlaveManagement.getDirection("down"));
        assertEquals(Transfer.TRANSFER_SENDING_DOWNLOAD, SlaveManagement.getDirection("jobdown"));
    }

    @Test
    public void testUnknownTypeIsImproperUsage() {
        assertThrows(ImproperUsageException.class, () -> SlaveManagement.getDirection("sideways"));
    }
}
