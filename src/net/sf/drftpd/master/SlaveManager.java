package net.sf.drftpd.master;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.RemoteSlave;
import net.sf.drftpd.slave.Slave;

public interface SlaveManager extends Remote {
    public void addSlave(RemoteSlave slave, LinkedRemoteFile root) throws RemoteException;
}
