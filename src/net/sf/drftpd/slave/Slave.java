package net.sf.drftpd.slave;

import net.sf.drftpd.RemoteFile;
import java.rmi.Remote;
import java.rmi.RemoteException;
import java.net.InetAddress;
import java.io.FileNotFoundException;
import java.net.ConnectException;

public interface Slave extends Remote {
    //public int doPassiveTransfer(RemoteFile file) throws RemoteException;
    public void doConnectSend(RemoteFile file, long offset, InetAddress addr, int port) throws RemoteException, FileNotFoundException, ConnectException;
    public void doConnectSend(String file, long offset, InetAddress addr, int port) throws RemoteException, FileNotFoundException, ConnectException;
}
