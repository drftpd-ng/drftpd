/*
 * SocketSlaveListener.java
 *
 * Created on April 28, 2004, 2:03 PM
 */

package net.sf.drftpd.tcpslave;

import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import socks.server.Ident;

/**
 *
 * @author  jbarrett
 */
public class SocketSlaveListener extends Thread {

    private static final Logger logger = Logger.getLogger(SocketSlaveListener.class.getName());

    private int _port;
    private ConnectionManager _conman;
    private ServerSocket sock;
    
    /** Creates a new instance of SocketSlaveListener */
    public SocketSlaveListener(ConnectionManager conman, int port) {
        _conman = conman;
        _port = port;
        start();
    }
    
    public void run() {
        try {
            sock = new ServerSocket(_port);
        } catch (Exception e) {
            throw new FatalException(e);
        }
        Socket slave;
        while (true) {
            try {
                slave = sock.accept();
            } catch (Exception e) {
                throw new FatalException(e);
            }
            InetAddress addr = slave.getInetAddress();
            logger.info("SockSlaveListener: accepting " + addr);
            Ident identObj = new Ident(slave);
            String ident;
            if (identObj.successful) {
                ident = identObj.userName;
            } else {
                ident = "";
            }
            Perl5Matcher m = new Perl5Matcher();
            
            String ipmask = ident + "@" + addr.getHostAddress();
            String hostmask = ident + "@" + addr.getHostName();
            logger.info("SockSlaveListener: ipmask " + ipmask);
            logger.info("SockSlaveListener: hostmask " + hostmask);
            Collection slaves = _conman.getSlaveManager().getSlaves();
            boolean match = false;
            RemoteSlave thisone = null;
            for (Iterator i=slaves.iterator(); i.hasNext();) {
                RemoteSlave rslave = (RemoteSlave)i.next();
                if (rslave.isAvailable()) {
                    logger.info("SockSlaveListener: online> " + rslave.getName());
                    continue; // already connected
                }
                String saddr = (String)rslave.getConfig().get("addr");
                if (saddr == null) {
                    logger.info("SockSlaveListener: noaddr> " + rslave.getName());
                    continue; // not a socketslave
                }
                if (!saddr.equals("Dynamic")) {
                    logger.info("SockSlaveListener: static> " + rslave.getName());
                    continue; // is a static slave
                }
                // unconnected dynamic socket slave, test masks
                logger.info("SockSlaveListener: testing " + rslave.getName());
                for (Iterator i2 = rslave.getMasks().iterator(); i2.hasNext(); ) {
                    String mask = (String) i2.next();
                    logger.info("SockSlaveListener: mask = " + mask);
                    Pattern p;
                    try {
                        p = new GlobCompiler().compile(mask);
                    } catch (MalformedPatternException ex) {
                        throw new RuntimeException(
                        "Invalid glob pattern: " + mask,
                        ex
                        );
                    }
                    
                    // ip
                    if (m.matches(ipmask, p) || m.matches(hostmask, p)) {
                        match = true;
                        thisone = rslave;
                        break;
                    }
                } //for
                if (match) break;
            } //for
            if (!match) continue; // no matching masks
            try {
                SocketSlaveImpl tmp = new SocketSlaveImpl(_conman, thisone.getConfig(), slave);
            } catch (Exception e) {
            }
        }
    }
}
