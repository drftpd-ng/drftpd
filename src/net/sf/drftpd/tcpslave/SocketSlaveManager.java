/*
 * SocketSlaveManager.java
 *
 * Created on April 20, 2004, 2:42 PM
 */

package net.sf.drftpd.tcpslave;

import java.rmi.RemoteException;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
/**
 *
 * @author  jbarrett
 */
public class SocketSlaveManager extends java.lang.Thread {
    
    private ConnectionManager _conman;
    private String _serverName;
    private int _socketPort;
    
    /** Creates a new instance of SocketSlaveManager */
    public SocketSlaveManager(ConnectionManager conman, Properties cfg) {
        _conman = conman;
	_serverName = cfg.getProperty("master.bindname", "slavemaster");
	_socketPort = Integer.parseInt(cfg.getProperty("master.socketport", "1100"));
        start();
    }
    
    public void run()
    {
        SocketSlaveListener ssl = new SocketSlaveListener(_conman, _socketPort);
        
        while (true) {
            Collection slaves = _conman.getSlaveManager().getSlaves();
            for (Iterator i=slaves.iterator(); i.hasNext();) {
                RemoteSlave rslave = (RemoteSlave)i.next();
                Hashtable cfg = rslave.getConfig();
                if (rslave.isAvailable()) continue;
                if (cfg.get("addr") == null) continue;
                String host = (String)cfg.get("addr");
                if (host.equals("Dynamic")) continue;
                // unconnected socket slave, try to connect
                try {
                    SocketSlaveImpl tmp = new SocketSlaveImpl(_conman, rslave.getConfig());
                } catch (RemoteException e) {
                }
            }
            try {
                sleep(60000);
            } catch (Exception e) {}
        }
    }
    
}
