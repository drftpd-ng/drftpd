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
package org.drftpd.master;

import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.remotefile.LinkedRemoteFile.CaseInsensitiveHashtable;

import org.drftpd.slave.async.AsyncResponseRemerge;

/**
 * @author mog
 * @version $Id: RemergeMessage.java,v 1.1 2004/11/09 03:08:26 zubov Exp $
 */
public class RemergeMessage {
    private RemoteSlave _rslave;
    private AsyncResponseRemerge _response;

    public RemergeMessage(AsyncResponseRemerge response, RemoteSlave slave) {
        _rslave = slave;
        _response = response;
    }

    public String getDirectory() {
        return _response.getDirectory();
    }

    public RemoteSlave getRslave() {
        return _rslave;
    }

    public CaseInsensitiveHashtable getFiles() {
        return _response.getFiles();
    }

}
