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


import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.slave.Slave;

/**
 * @version $Id: SlaveManager.java,v 1.13 2004/03/15 01:55:25 zubov Exp $
 */
public interface SlaveManager extends Remote {
    public void addSlave(String slavename, Slave slave) throws RemoteException;
}
