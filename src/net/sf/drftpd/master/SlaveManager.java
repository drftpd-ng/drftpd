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
package net.sf.drftpd.master;

import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;

import java.rmi.Remote;
import java.rmi.RemoteException;


/**
 * @version $Id: SlaveManager.java,v 1.17 2004/08/03 20:13:56 zubov Exp $
 */
public interface SlaveManager extends Remote {
    public void mergeSlaveAndSetOnline(String slavename, Slave slave,
        SlaveStatus status, int maxPath) throws RemoteException;
}
