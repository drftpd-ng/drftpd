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
package org.drftpd.slaveselection.filter;

import java.rmi.RemoteException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Properties;

import org.apache.log4j.BasicConfigurator;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;

import junit.framework.TestCase;
import junit.framework.TestSuite;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.FileExistsException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.StaticRemoteFile;
import net.sf.drftpd.slave.Transfer;

/**
 * @author mog
 * @version $Id: SlavetopFilterTest.java,v 1.4 2004/04/20 04:11:53 mog Exp $
 */
public class SlavetopFilterTest extends TestCase {

	public class CM extends ConnectionManager {
		public SectionManagerInterface getSectionManager() {
			return new SectionManagerInterface() {

				public ConnectionManager getConnectionManager() {
					throw new UnsupportedOperationException();
				}
				public Collection getSections() {
					throw new UnsupportedOperationException();
				}
				public SectionInterface lookup(String string) {
					return new SectionInterface() {

						public LinkedRemoteFileInterface getFile() {
							return root;
						}

						public Collection getFiles() {
							throw new UnsupportedOperationException();
						}

						public String getName() {
							throw new UnsupportedOperationException();
						}

						public String getPath() {
							return getFile().getPath();
						}

					};
				}
				public SectionInterface getSection(String string) {
					throw new UnsupportedOperationException();
				}
			};
		}
	}

	public class FC extends FilterChain {

		public SlaveManagerImpl getSlaveManager() {
			try {
				return new SlaveManager();
			} catch (RemoteException e) {
				throw new RuntimeException(e);
			}
		}
	}

	public class SlaveManager extends SlaveManagerImpl {

		protected SlaveManager() throws RemoteException {
			super();
		}

		public ConnectionManager getConnectionManager() {
			return new CM();
		}

	}

	public static TestSuite suite() {
		return new TestSuite(SlavetopFilterTest.class);
	}
	private LinkedRemoteFile root;

	public SlavetopFilterTest(String name) {
		super(name);
	}

	protected void setUp() throws Exception {
		BasicConfigurator.configure();
	}

	public void testSimple()
		throws
			NoAvailableSlaveException,
			FileExistsException,
			ObjectNotFoundException {
		Properties p = new Properties();
		p.put("1.topslaves", "2");
		p.put("1.assign", "100");

		RemoteSlave rslaves[] =
			{
				new RemoteSlave("slave1", null),
				new RemoteSlave("slave2", null),
				new RemoteSlave("slave3", null)};

		ScoreChart sc = new ScoreChart(Arrays.asList(rslaves));

		root = new LinkedRemoteFile(null);
		LinkedRemoteFile dir1 = root.createDirectory("dir1");

		dir1.addFile(
			new StaticRemoteFile(
				"file1",
				Collections.singletonList(rslaves[0])));
		dir1.addFile(
			new StaticRemoteFile(
				"file2",
				Collections.singletonList(rslaves[2])));
		dir1.addFile(
			new StaticRemoteFile(
				"file3",
				Collections.singletonList(rslaves[0])));
		dir1.addFile(
			new StaticRemoteFile(
				"file4",
				Collections.singletonList(rslaves[1])));
		dir1.addFile(
			new StaticRemoteFile(
				"file5",
				Collections.singletonList(rslaves[2])));

		Filter f = new SlavetopFilter(new FC(), 1, p);
		f.process(sc, null, null, Transfer.TRANSFER_UNKNOWN, dir1);
		assertEquals(100, sc.getSlaveScore(rslaves[0]).getScore());
		assertEquals(0, sc.getSlaveScore(rslaves[1]).getScore());
		assertEquals(100, sc.getSlaveScore(rslaves[2]).getScore());
	}

}
