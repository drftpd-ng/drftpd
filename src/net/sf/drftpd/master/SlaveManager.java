package net.sf.drftpd.master;


import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.*;

public interface SlaveManager extends Remote {
    public void addSlave(RemoteSlave slave, LinkedRemoteFile root) throws RemoteException;
}
