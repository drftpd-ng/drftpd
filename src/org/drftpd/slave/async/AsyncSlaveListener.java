/*
 * SocketSlaveListener.java
 *
 * Created on April 28, 2004, 2:03 PM
 */

package org.drftpd.slave.async;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
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
public class AsyncSlaveListener extends Thread {
    
    private static final Logger logger = Logger.getLogger(AsyncSlaveListener.class.getName());
    
    private int _port;
    private ServerSocket sock;
    static ConnectionManager _conman;
    static String _pass = "";
    
    static void invalidSlave(String msg, Socket sock) throws IOException {
        BufferedReader _sinp = null;
        PrintWriter _sout = null;
        try {
            _sout = new PrintWriter(sock.getOutputStream(), true);
            _sinp =	new BufferedReader(
            new InputStreamReader(sock.getInputStream())
            );
            _sout.println(msg);
            logger.info("NEW< " + msg);
            String txt = AsyncSlaveListener.readLine(_sinp, 30);
            String sname = "";
            String spass = "";
            String shash = "";
            try {
                String[] items = txt.split(" ");
                sname = items[1].trim();
                spass = items[2].trim();
                shash = items[3].trim();
            } catch (Exception e) {
                throw new IOException("Slave Inititalization Faailed");
            }
            // generate slave hash
            String pass = sname + spass + _pass;
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.reset();
            md5.update(pass.getBytes());
            String hash = AsyncSlaveListener.hash2hex(md5.digest()).toLowerCase();
            if (!hash.equals(shash)) {
                throw new IOException("Slave Inititalization Faailed");
            }
            
            // check the passkey and create the slave
            
        } catch (Exception e) {
            
        }
        throw new IOException("Slave Inititalization Faailed");
    }
    
    static String hash2hex(byte[] bytes) {
        String res = "";
        for (int i = 0; i < 16; i++) {
            String hex = Integer.toHexString((int) bytes[i]);
            if (hex.length() < 2)
                hex = "0" + hex;
            res += hex.substring(hex.length() - 2);
        }
        return res;
    }
    
    
    static String readLine(BufferedReader _sinp, int secs) {
        int cnt = secs * 10;
        try {
            while (true) {
                while (!_sinp.ready()) {
                    if (cnt < 1)
                        return null;
                    sleep(100);
                    cnt--;
                    if (cnt == 0)
                        return null;
                }
                String txt = _sinp.readLine();
                logger.info("NEW> " + txt);
                return txt;
            }
        } catch (Exception e) {
            return null;
        }
    }
    
    /** Creates a new instance of SocketSlaveListener */
    public AsyncSlaveListener(ConnectionManager conman, int port, String pass) {
        _conman = conman;
        _port = port;
        _pass = pass;
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
            logger.info("AsyncSlaveListener: accepting " + addr);
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
            logger.info("AsyncSlaveListener: ipmask " + ipmask);
            logger.info("AsyncSlaveListener: hostmask " + hostmask);
            Collection slaves = _conman.getGlobalContext().getSlaveManager().getSlaves();
            boolean match = false;
            RemoteSlave thisone = null;
            for (Iterator i=slaves.iterator(); i.hasNext();) {
                RemoteSlave rslave = (RemoteSlave)i.next();
                if (rslave.isAvailable()) {
                    logger.info("AsyncSlaveListener: online> " + rslave.getName());
                    continue; // already connected
                }
                String saddr = (String)rslave.getConfig().get("addr");
                if (saddr == null) {
                    logger.info("AsyncSlaveListener: noaddr> " + rslave.getName());
                    continue; // not a socketslave
                }
                if (!saddr.equals("Dynamic")) {
                    logger.info("AsyncSlaveListener: static> " + rslave.getName());
                    continue; // is a static slave
                }
                // unconnected dynamic socket slave, test masks
                logger.info("AsyncSlaveListener: testing " + rslave.getName());
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
            if (match) {
                // turn control over to the slave code
                try {
                    AsyncSlave tmp = new AsyncSlave(_conman, thisone.getConfig(), slave);
                } catch (Exception e) {
                }
            } else {
                // allow the slave to auto-register
                try {
                    AsyncSlaveListener.invalidSlave("INITFAIL Unregistered", slave);
                } catch (Exception e) {
                }
            }
        }
    }
}
