/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.common.util;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.socks.Ident;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @version $Id$
 */
public class HostMaskCollection extends ArrayList<HostMask> {
    private static final Logger logger = LogManager.getLogger(HostMaskCollection.class);

    public HostMaskCollection() {
    }

    /**
     * Converts an existing Collection of String-based masks to a HostMaskCollection
     *
     * @param masks The collection of type String that contains the masks
     */
    public HostMaskCollection(Collection<String> masks) {
        for (String mask : masks) {
            add(new HostMask(mask));
        }
    }

    public void addAllMasks(HostMaskCollection hostMaskCollection) {
        for (HostMask mask : hostMaskCollection) {
            if (!contains(mask)) {
                add(mask);
            }
        }
    }

    public void addMask(String mask) throws DuplicateElementException {
        HostMask newMask = new HostMask(mask);

        for (HostMask hostMask : this) {
            if (hostMask.equals(newMask)) {
                throw new DuplicateElementException();
            }
        }

        add(newMask);
    }

    public List<HostMask> getMatchingMasks(Socket s) throws PatternSyntaxException {
        List<HostMask> matched = new ArrayList<>();
        InetAddress a = s.getInetAddress();
        for (HostMask mask : this) {
            if (mask.matchesHost(a)) {
                matched.add(mask);
            }
        }
        return matched;
    }

    public boolean check(Socket s) throws PatternSyntaxException {
        String ident = "";
        // Try to get an ident for the slave connection
        try {
            ident = new Ident(s).getUserName();
        } catch (IOException ignore) {}
        return check(ident, s.getInetAddress());
    }

    public boolean check(String ident, InetAddress a) throws PatternSyntaxException {
        if (ident == null ) {
            logger.error("ident cannot be null!");
            throw new NullPointerException();
        }
        if (a == null) {
            logger.error("InetAddress cannot be null!");
            throw new NullPointerException();
        }
        for (HostMask mask : this) {
            if (!mask.matchesHost(a)) {
                continue;
            }

            if (mask.matchesIdent(ident)) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param mask The mask to remove
     * @return true only if we have a mask that matches the input
     */
    public boolean removeMask(String mask) {
        for (Iterator<HostMask> iter = iterator(); iter.hasNext(); ) {
            if ((iter.next()).getMask().equals(mask)) {
                iter.remove();
                return true;
            }
        }

        return false;
    }

    public String toString() {
        if (isEmpty())
            return "";
        String masks = "";

        for (HostMask hostMask : this) {
            masks = masks + hostMask + "  ";
        }

        return masks.substring(0, masks.length() - 2);
    }
}
