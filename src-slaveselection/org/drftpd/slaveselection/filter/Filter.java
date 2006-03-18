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
package org.drftpd.slaveselection.filter;

import java.net.InetAddress;

import net.sf.drftpd.NoAvailableSlaveException;

import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandle;


/**
 * if upload, the inetaddress would be the source.
 * if download, the inetaddress would be the dest.
 *
 * @author mog
 * @version $Id: Filter.java 847 2004-12-02 03:32:41Z mog $
 */
public abstract class Filter {
    public abstract void process(ScoreChart scorechart, User user,
        InetAddress peer, char direction, InodeHandle dir, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException;
}
