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
package net.sf.drftpd.event;

import net.sf.drftpd.master.BaseFtpConnection;

import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

import java.net.InetAddress;


/**
 * @author mog
 * @version $Id$
 */
public class TransferEvent extends DirectoryFtpEvent {
    private InetAddress _peer;
    private char _type;
    private InetAddress _clientHost;
    private BaseFtpConnection _conn;

    /**
     * @param user
     * @param command
     * @param directory
     */
    public TransferEvent(BaseFtpConnection conn, String command,
        LinkedRemoteFileInterface directory, InetAddress clientHost,
        RemoteSlave rslave, InetAddress peer, char type) {
        this(conn, command, directory, clientHost, rslave, peer, type,
            System.currentTimeMillis());
    }

    private TransferEvent(BaseFtpConnection conn, String command,
        LinkedRemoteFileInterface directory, InetAddress clientHost,
        RemoteSlave rslave, InetAddress peer, char type,
        long time) {
        super(conn.getUserNull(), command, directory, time);
        _clientHost = clientHost;

        if (peer == null) {
            throw new NullPointerException();
        }

        _peer = peer;
        _type = type;
        _conn = conn;
    }

    public char getType() {
        return _type;
    }

    public InetAddress getClientHost() {
        return _clientHost;
    }

    public InetAddress getXferHost() {
        return _peer;
    }

    public InetAddress getPeer() {
        return _peer;
    }

	public BaseFtpConnection getConn() {
		return _conn;
	}
}
