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
package net.sf.drftpd;

import org.apache.log4j.Logger;

import org.apache.oro.text.regex.MalformedPatternException;

import socks.server.Ident;

import java.io.IOException;
import java.io.Serializable;

import java.net.InetAddress;
import java.net.Socket;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id: HostMaskCollection.java,v 1.4 2004/11/03 16:46:37 mog Exp $
 */
public class HostMaskCollection implements Serializable {
    private static final Logger logger = Logger.getLogger(HostMaskCollection.class);
    ArrayList _masks;

    public HostMaskCollection() {
        _masks = new ArrayList();
    }

    /**
     * Converts an existing Collection of String-based masks to a
     * HostMaskCollection
     *
     * @param masks
     */
    public HostMaskCollection(Collection masks) {
        _masks = new ArrayList();

        for (Iterator iter = masks.iterator(); iter.hasNext();) {
            String mask = (String) iter.next();
            _masks.add(new HostMask(mask));
        }
    }

    public void addAllMasks(HostMaskCollection hostMaskCollection) {
        for (Iterator i = hostMaskCollection._masks.iterator(); i.hasNext();) {
            HostMask mask = (HostMask) i.next();

            if (!_masks.contains(mask)) {
                _masks.add(mask);
            }
        }
    }

    public void addMask(String mask) {
        HostMask newMask = new HostMask(mask);

        for (Iterator i = _masks.iterator(); i.hasNext();) {
            HostMask hostMask = (HostMask) i.next();

            if (hostMask.equals(newMask)) {
                // mask already added
                return;
            }
        }

        _masks.add(new HostMask(mask));
    }

    public boolean check(Socket s) throws MalformedPatternException {
        return check(null, s.getInetAddress(), s);
    }

    public boolean check(String ident, InetAddress a, Socket s)
        throws MalformedPatternException {
        if (a == null) {
            throw new NullPointerException();
        }

        for (Iterator iter = _masks.iterator(); iter.hasNext();) {
            HostMask mask = (HostMask) iter.next();

            if (!mask.matchesHost(a)) {
                continue;
            }

            // host matched
            // request ident if no IDNT, ident hasn't been requested
            // and ident matters in this hostmask
            if (mask.isIdentMaskSignificant() && (ident == null)) {
                try {
                    ident = new Ident(s).getUserName();
                } catch (IOException e) {
                    ident = "";
                }
            }

            if (mask.matchesIdent(ident)) {
                return true;
            }
        }

        return false;
    }

    public boolean isEmpty() {
        return _masks.isEmpty();
    }

    /**
     * @param mask
     * @return
     */
    public boolean removeMask(String mask) {
        for (Iterator iter = _masks.iterator(); iter.hasNext();) {
            if (((HostMask) iter.next()).getMask().equals(mask)) {
                iter.remove();

                return true;
            }
        }

        return false;
    }

    public String toString() {
        String masks = "";

        for (Iterator iter = _masks.iterator(); iter.hasNext();) {
            masks = masks + iter.next() + ", ";
        }

        return masks.substring(0, masks.length() - 2);
    }
}
