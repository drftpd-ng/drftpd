/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.util;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Random;

import javax.net.ServerSocketFactory;


/**
 * @author mog
 * @version $Id: PortRange.java,v 1.14 2004/11/05 13:13:41 zubov Exp $
 */
public class PortRange {
    private static final Logger logger = Logger.getLogger(PortRange.class);
    private int _minPort;
    private int _maxPort;
    Random rand = new Random();

    /**
     * Creates a default port range for port 49152 to 65535.
     */
    public PortRange() {
        this(0,0);
    }

    public PortRange(int minPort, int maxPort) {
        _maxPort = maxPort;
        _minPort = minPort;
    }
    
    public ServerSocket getPort(ServerSocketFactory ssf) {
        if (_minPort == 0 && _maxPort == 0) {
            try {
                return ssf.createServerSocket(0,1);
            } catch (IOException e) {
                logger.error("Unable to bind anonymous port",e);
                throw new RuntimeException(e);
            }
        }
        int initPos = rand.nextInt(_maxPort-_minPort+1) + _minPort;
        int pos = initPos + 1;
        if (pos > _maxPort) {
            pos = _minPort;
        }
        while(pos != initPos) {
            try {
                return ssf.createServerSocket(pos,1);
            } catch (IOException e) {
            }
            pos++;
            if (pos > _maxPort) {
                pos = _minPort;
            }
        }
        throw new RuntimeException("PortRange exhausted");
    }
}
