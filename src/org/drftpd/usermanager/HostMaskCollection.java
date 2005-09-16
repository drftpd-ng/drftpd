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
package org.drftpd.usermanager;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;

import net.sf.drftpd.DuplicateElementException;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;

import socks.server.Ident;


/**
 * @author mog
 * @version $Id$
 */
public class HostMaskCollection extends ArrayList {
    private static final Logger logger = Logger.getLogger(HostMaskCollection.class);

    public HostMaskCollection() {
    }

    /**
     * Converts an existing Collection of String-based masks to a
     * HostMaskCollection
     *
     * @param masks
     */
    public HostMaskCollection(Collection<String> masks) {
    	for (String mask : masks) {
			add(new HostMask(mask));
		}
    }

    public void addAllMasks(HostMaskCollection hostMaskCollection) {
        for (Iterator i = hostMaskCollection.iterator(); i.hasNext();) {
            HostMask mask = (HostMask) i.next();

            if (!contains(mask)) {
                add(mask);
            }
        }
    }

    public void addMask(String mask) throws DuplicateElementException {
        HostMask newMask = new HostMask(mask);

        for (Iterator i = iterator(); i.hasNext();) {
            HostMask hostMask = (HostMask) i.next();

            if (hostMask.equals(newMask)) {
            	throw new DuplicateElementException();
            }
        }

        add(newMask);
    }

    public boolean check(Socket s) throws MalformedPatternException {
        return check(null, s.getInetAddress(), s);
    }

    public boolean check(String ident, InetAddress a, Socket s)
        throws MalformedPatternException {
        if (a == null) {
            throw new NullPointerException();
        }
        for (Iterator iter = this.iterator(); iter.hasNext();) {
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

    /**
     * @param mask
     * @return
     */
    public boolean removeMask(String mask) {
        for (Iterator iter = iterator(); iter.hasNext();) {
            if (((HostMask) iter.next()).getMask().equals(mask)) {
                iter.remove();
                return true;
            }
        }

        return false;
    }

    public String toString() {
    	if(isEmpty()) return "";
        String masks = "";

        for (Iterator iter = iterator(); iter.hasNext();) {
            masks = masks + iter.next() + ", ";
        }

        return masks.substring(0, masks.length() - 2);
    }
}
