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
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectExistsException;
import net.sf.drftpd.master.usermanager.AbstractUser;
import net.sf.drftpd.master.usermanager.UserFileException;

import junit.framework.TestCase;

/*
 * @author zubov
 * @version $Id
 */
public class StatsTest extends TestCase {

	/**
	 * Constructor for StatsTest.
	 * @param arg0
	 */
	public StatsTest(String arg0) {
		super(arg0);
	}

	public static void main(String[] args) {
		junit.textui.TestRunner.run(StatsTest.class);
	}

	class TestUser extends AbstractUser {
		
		public TestUser(String name) {
			 super(name);
		}
		
		public boolean checkPassword(String password) {
			return true;
		}

		public void commit() throws UserFileException {

		}

		public void purge() {

		}

		public void rename(String username)
			throws ObjectExistsException, UserFileException {

		}

		public void setPassword(String password) {

		}

}

	public void testStats() throws UnknownHostException, IOException {
		ArrayList users = new ArrayList();
		users.add(new TestUser("user1"));
		users.add(new TestUser("user2"));
		users.add(new TestUser("user3"));
		StringTokenizer st = new StringTokenizer(new String("2"));
		assertEquals(Stats.fixNumberAndUserlist(st,users),2);
		assertEquals(3,users.size());
		st = new StringTokenizer(new String("2 !-user1 *"));
		assertEquals(2,Stats.fixNumberAndUserlist(st,users));
		assertEquals(2,users.size());
	}
}
