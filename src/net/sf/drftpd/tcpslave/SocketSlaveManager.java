/*
 * SocketSlaveManager.java
 *
 * Created on April 20, 2004, 2:42 PM
 */

package net.sf.drftpd.tcpslave;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.Vector;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.tcpslave.SocketSlaveImpl;
/**
 *
 * @author  jbarrett
 */
public class SocketSlaveManager extends java.lang.Thread {
    
    private ConnectionManager _conman;
    
    /** Creates a new instance of SocketSlaveManager */
    public SocketSlaveManager(ConnectionManager conman) {
        _conman = conman;
        start();
    }
    
    public void run()
    {
        while (true) {
            Collection slaves = _conman.getSlaveManager().getSlaves();
            for (Iterator i=slaves.iterator(); i.hasNext();) {
                RemoteSlave rslave = (RemoteSlave)i.next();
                if (rslave.isAvailable()) continue;
                if (rslave.getConfig().get("addr") != null) {
                    // unconnected socket slave, try to connect
                    try {
                        SocketSlaveImpl tmp = new SocketSlaveImpl(_conman, rslave.getConfig());
                    } catch (Exception e) {
                    }
                }
            }
            try {
                sleep(60000);
            } catch (Exception e) {}
        }
    }
    
}
