package net.sf.drftpd.master;


import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;

/**
 * @version $Id: SlaveManager.java,v 1.11 2003/12/23 13:38:19 mog Exp $
 */
public interface SlaveManager extends Remote {
    public void addSlave(String slavename, Slave slave, LinkedRemoteFile slaveroot) throws RemoteException;
}
