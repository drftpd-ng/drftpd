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
package org.drftpd.plugins;

import junit.framework.TestCase;

import org.drftpd.plugins.AffilManagement.AffilPermission;
import org.drftpd.remotefile.LinkedRemoteFile;
import org.drftpd.remotefile.StaticRemoteFile;

/**
 * @author mog
 * @version $Id: AffilManagementTest.java 879 2004-12-29 03:39:22Z mog $
 */
public class AffilManagementTest extends TestCase {

	private LinkedRemoteFile _root;

	private LinkedRemoteFile _groups;

	private LinkedRemoteFile _thegroup;

	private LinkedRemoteFile _release;

	private LinkedRemoteFile _othergroup;

	protected void setUp() throws Exception {
	}

	protected void tearDown() throws Exception {
	}

	private void buildRoot() {
		_root = new LinkedRemoteFile(null);
		_groups = _root.addFile(new StaticRemoteFile("groups", null));
		_thegroup = _groups.addFile(new StaticRemoteFile("thegroup", null));
		_othergroup = _groups.addFile(new StaticRemoteFile("othergroup", null));
		_release = _thegroup.addFile(new StaticRemoteFile("release", null));
	}
	public void testAffilMangment() {
		buildRoot();
		AffilPermission mg = new AffilManagement.AffilPermission("thegroup");
		assertTrue(mg.checkPath(_release));
		assertTrue(!mg.checkPath(_othergroup));
	}
}
