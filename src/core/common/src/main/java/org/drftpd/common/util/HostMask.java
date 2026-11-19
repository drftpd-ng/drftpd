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

import org.drftpd.common.network.InetAddressUtils;

import java.net.InetAddress;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author mog
 * @version $Id$
 */
public class HostMask {

    private String _hostMask;

    private String _identMask;

    public HostMask(String string) {
        int pos = string.indexOf('@');

        if (pos == -1) {
            _identMask = "*";
            _hostMask = string;
        } else {
            _identMask = string.substring(0, pos);
            _hostMask = string.substring(pos + 1);
        }
        if (_identMask.equals("")) {
            _identMask = "*";
        }
        if (_hostMask.equals("")) {
            _hostMask = "*";
        }
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof HostMask)) {
            return false;
        }

        HostMask h = (HostMask) obj;
        return h.getIdentMask().equals(getIdentMask())
                && h.getHostMask().equals(getHostMask());
    }

    public String getHostMask() {
        return _hostMask;
    }

    public String getIdentMask() {
        return _identMask;
    }

    public String getMask() {
        return getIdentMask() + "@" + getHostMask();
    }

    /**
     * Is ident used?
     *
     * @return false is ident mask equals "*"
     */
    public boolean isIdentMaskSignificant() {
        return !_identMask.equals("*");
    }

    /**
     * Matches the address against the host part of the mask. IPv6 addresses are matched in their
     * short form ("2001:db8::1", "::1") and, for masks written before that, in the full form Java
     * uses ("2001:db8:0:0:0:0:0:1"), both without a zone/scope id.
     */
    public boolean matchesHost(InetAddress a) throws PatternSyntaxException {
        Pattern p = GlobPattern.compile(getHostMask());

        if (p.matcher(InetAddressUtils.toShortString(a)).matches()) {
            return true;
        }
        String hostAddress = a.getHostAddress();
        int scope = hostAddress.indexOf('%');
        if (scope != -1) {
            hostAddress = hostAddress.substring(0, scope);
        }
        return p.matcher(hostAddress).matches() || p.matcher(a.getHostName()).matches();
    }

    public boolean matchesIdent(String ident) throws PatternSyntaxException {
        if (ident == null) {
            ident = "";
        }

        return !isIdentMaskSignificant() || GlobPattern.matches(getIdentMask(), ident);
    }

    public String toString() {
        return _identMask + "@" + _hostMask;
    }
}
