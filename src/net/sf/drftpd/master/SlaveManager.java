package net.sf.drftpd.master;


import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.Slave;

public interface SlaveManager extends Remote {
    public void addSlave(String slavename, Slave slave, LinkedRemoteFile slaveroot) throws RemoteException;
}
