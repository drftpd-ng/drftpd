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

import java.net.InetAddress;
import java.net.UnknownHostException;

import junit.framework.TestCase;

import org.apache.oro.text.regex.MalformedPatternException;


/**
 * @author zubov
 * @version $Id$
 */
public class HostMaskTest extends TestCase {
    public static void main(String[] args) {
    }

    public void testMatchesHost()
        throws UnknownHostException, MalformedPatternException {
        HostMask h = new HostMask("*@1.1.1.1");
        assertTrue(h.matchesHost(InetAddress.getByName("1.1.1.1")));
        assertFalse(h.matchesHost(InetAddress.getByName("1.1.1.2")));
        h = new HostMask("1.*.*.*");
        assertTrue(h.matchesHost(InetAddress.getByName("1.2.3.4")));
        assertFalse(h.matchesHost(InetAddress.getByName("2.2.3.4")));
    }

    public void testMatchesIdent() throws MalformedPatternException {
        HostMask h = new HostMask("*@1.1.1.1");
        assertTrue(h.matchesIdent(null));
        assertTrue(h.matchesIdent("anything"));
        h = new HostMask("anything@1.1.1.1");
        assertFalse(h.matchesIdent(null));
        assertTrue(h.matchesIdent("anything"));
        assertFalse(h.matchesIdent("nothing"));
    }
    
    public void testEquals() {
    	HostMask a = new HostMask("test@1.1.1.1");
    	HostMask b = new HostMask("test@1.1.1.1");
    	assertEquals(a,b);
    	a = new HostMask("*@1.1.1.*");
    	b = new HostMask("1.1.1.*");
    	assertEquals(a,b);
    	a = new HostMask("@1.1.1.*");
    	assertEquals(a,b);
    	a = new HostMask("notequal@4.2.3.4");
    	assertFalse(a.equals(b));
    }
}
