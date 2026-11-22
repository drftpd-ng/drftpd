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
package org.drftpd.master.slavemanagement;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.slave.SystemInfo;
import org.drftpd.slave.network.AsyncResponseSystemInfo;
import org.drftpd.slave.protocol.SlaveProtocolCentral;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

public class SystemInfoReplyTest {

    @Test
    public void okReplyCarriesTheInfo() {
        SystemInfo si = new SystemInfo();
        SystemInfoReply reply = SystemInfoReply.fromResponse(new AsyncResponseSystemInfo("01", si));
        assertEquals(SystemInfoReply.Status.OK, reply.getStatus());
        assertSame(si, reply.getSystemInfo());
    }

    @Test
    public void olderSlaveIsReportedAsUnsupported() {
        // exactly what SlaveProtocolCentral.handleCommand answers for a command without handler
        AsyncResponse response = new AsyncResponseException("01",
                new Exception("sysinfo" + SlaveProtocolCentral.OPERATION_NOT_SUPPORTED));
        SystemInfoReply reply = SystemInfoReply.fromResponse(response);
        assertEquals(SystemInfoReply.Status.UNSUPPORTED, reply.getStatus());
        assertNull(reply.getSystemInfo());
    }

    @Test
    public void literalMessageOfReleasedSlavesIsRecognised() {
        // the literal used by released slaves, before the constant existed
        assertTrue(SystemInfoReply.isUnsupportedOperation(new Exception("sysinfo - Operation Not Supported")));
        assertFalse(SystemInfoReply.isUnsupportedOperation(new Exception((String) null)));
        assertFalse(SystemInfoReply.isUnsupportedOperation(null));
    }

    @Test
    public void otherErrorsAreErrors() {
        assertEquals(SystemInfoReply.Status.ERROR,
                SystemInfoReply.fromResponse(new AsyncResponseException("01", new IOException("disk"))).getStatus());
        assertEquals(SystemInfoReply.Status.ERROR,
                SystemInfoReply.fromResponse(new AsyncResponseException("01",
                        new Exception("Unable to invoke the proper handler"))).getStatus());
        assertEquals(SystemInfoReply.Status.ERROR,
                SystemInfoReply.fromResponse(new AsyncResponse("01")).getStatus());
        assertEquals(SystemInfoReply.Status.ERROR,
                SystemInfoReply.fromResponse(new AsyncResponseSystemInfo("01", null)).getStatus());
        assertEquals(SystemInfoReply.Status.ERROR, SystemInfoReply.fromResponse(null).getStatus());
    }
}
