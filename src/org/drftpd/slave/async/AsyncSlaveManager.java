/*
 * SocketSlaveManager.java
 *
 * Created on April 20, 2004, 2:42 PM
 */

package org.drftpd.slave.async;

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
public class AsyncSlaveManager extends java.lang.Thread {
    
    private ConnectionManager _conman;
    private String _serverName;
    private String _socketPass;
    private int _socketPort;
    
    /** Creates a new instance of SocketSlaveManager */
    public AsyncSlaveManager(ConnectionManager conman, Properties cfg) {
        _conman = conman;
        _serverName = cfg.getProperty("master.bindname", "slavemaster");
        _socketPass = cfg.getProperty("master.socketpass", "");
        _socketPort = Integer.parseInt(cfg.getProperty("master.socketport", "1100"));
        start();
    }
    
    public void run() {
        AsyncSlaveListener ssl = new AsyncSlaveListener(_conman, _socketPort, _socketPass);
        
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
                    AsyncSlave tmp = new AsyncSlave(_conman, rslave.getConfig());
                } catch (RemoteException e) {
                }
            }
            try {
                sleep(60000);
            } catch (Exception e) {}
        }
    }
    
}
