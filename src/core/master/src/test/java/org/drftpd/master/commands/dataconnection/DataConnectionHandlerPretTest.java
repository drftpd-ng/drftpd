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
package org.drftpd.master.commands.dataconnection;

import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.TransferState;
import org.drftpd.master.util.FtpRequest;
import org.drftpd.master.vfs.DirectoryHandle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * STOR/RETR after PRET + PASV for a different command must be refused with 503, before anything uses the
 * transfer file or slave that such a PRET did not set up.
 */
public class DataConnectionHandlerPretTest {

    private DataConnectionHandler _handler;
    private BaseFtpConnection _conn;

    @BeforeEach
    public void setUp() {
        _handler = new DataConnectionHandler();
        // no socket, no user: the checks under test come before both
        _conn = new BaseFtpConnection() {
        };
        // as after PROT P, so transfer() does not look up the denydatauncrypted permission
        _conn.getTransferState().setSendFilesEncrypted(true);
    }

    private CommandRequest request(String command, String argument) {
        return new CommandRequest(command, argument, new DirectoryHandle("/"), null, _conn, new Properties());
    }

    /** What PASV leaves behind after a PRET: the PRET request and the passive flag (see doPASVandCPSV). */
    private void pasvAfterPret(String pretArgument) {
        TransferState ts = _conn.getTransferState();
        ts.setPreTransferRequest(new FtpRequest(pretArgument));
        ts.setPasv(true);
    }

    @Test
    public void storAfterPretListAndPasvIs503() {
        // PRET LIST needs neither a user nor the VFS, so it goes through the real command
        assertEquals(200, _handler.doPRET(request("PRET", "LIST")).getCode());
        _conn.getTransferState().setPasv(true);
        assertEquals(503, _handler.doSTOR(request("STOR", "file.bin")).getCode());
    }

    @Test
    public void retrAfterPretListAndPasvIs503() {
        pasvAfterPret("LIST");
        assertEquals(503, _handler.doRETR(request("RETR", "file.bin")).getCode());
    }

    @Test
    public void storAfterPretRetrAndPasvIs503() {
        pasvAfterPret("RETR file.bin");
        assertEquals(503, _handler.doSTOR(request("STOR", "file.bin")).getCode());
    }

    @Test
    public void retrAfterPretStorAndPasvIs503() {
        pasvAfterPret("STOR file.bin");
        assertEquals(503, _handler.doRETR(request("RETR", "file.bin")).getCode());
    }

    @Test
    public void matchingPretPassesTheSequenceCheck() {
        // a STOR announced by PRET STOR gets past the check and stops at the next one (no argument -> 501)
        pasvAfterPret("STOR file.bin");
        CommandResponse stor = _handler.doSTOR(request("STOR", null));
        assertEquals(501, stor.getCode());

        pasvAfterPret("RETR file.bin");
        CommandResponse retr = _handler.doRETR(request("RETR", null));
        assertEquals(501, retr.getCode());
    }
}
