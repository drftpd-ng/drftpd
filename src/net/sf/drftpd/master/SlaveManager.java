package net.sf.drftpd.master;

import java.rmi.Remote;
import java.rmi.RemoteException;
import java.util.Map;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.RemoteSlave;
import net.sf.drftpd.RemoteFile;

public interface SlaveManager extends Remote {

    public void addSlave(String key, Slave slave, RemoteFile root) throws RemoteException;
    //public void addSlave(String key, Slave slave) throws RemoteException;
	//public RemoteSlave getSlave(String key) throws RemoteException;

    public RemoteSlave getASlave() throws RemoteException;
    public RemoteFile getRoot() throws RemoteException;

}
