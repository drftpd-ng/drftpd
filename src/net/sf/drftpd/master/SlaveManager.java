package net.sf.drftpd.master;

import LinkedRemoteFile;

import java.rmi.Remote;
import java.rmi.RemoteException;

import net.sf.drftpd.slave.RemoteSlave;

public interface SlaveManager extends Remote {
    public void addSlave(RemoteSlave slave, LinkedRemoteFile root) throws RemoteException;
}
