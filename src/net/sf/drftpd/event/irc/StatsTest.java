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
package net.sf.drftpd.event.irc;

import java.io.IOException;
import java.net.UnknownHostException;
import java.util.ArrayList;

import junit.framework.TestCase;
import junit.framework.TestSuite;

import org.drftpd.tests.DummyUser;

/**
 * @author zubov
 * @version $Id: StatsTest.java,v 1.7 2004/07/12 20:37:24 mog Exp $
 */
public class StatsTest extends TestCase {

	/**
	 * Constructor for StatsTest.
	 */
	public StatsTest(String fName) {
		super(fName);
	}

	public static TestSuite suite() {
		return new TestSuite(StatsTest.class);
	}

	public void testStats() throws UnknownHostException, IOException {
		ArrayList users = new ArrayList();
		users.add(new DummyUser("user1"));
		users.add(new DummyUser("user2"));
		users.add(new DummyUser("user3"));
		assertEquals(Stats.fixNumberAndUserlist("!alup 2",users),2);
		assertEquals(3,users.size());
		assertEquals(2,Stats.fixNumberAndUserlist("!alup 2 !-user1 *",users));
		assertEquals(2,users.size());
	}
}
