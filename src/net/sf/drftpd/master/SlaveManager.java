package net.sf.drftpd.master;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.RemoteSlave;
import net.sf.drftpd.LinkedRemoteFile;

public interface SlaveManager extends Remote {

    public void addSlave(RemoteSlave slave, LinkedRemoteFile root) throws RemoteException;
    //public void addSlave(String key, Slave slave) throws RemoteException;
	//public RemoteSlave getSlave(String key) throws RemoteException;

    public RemoteSlave getASlave() throws RemoteException;
    public LinkedRemoteFile getRoot() throws RemoteException;

}
