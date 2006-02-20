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
package org.drftpd.vfs;

import java.io.FileNotFoundException;

import junit.framework.TestCase;
import net.sf.drftpd.FileExistsException;

public class VirtualFileSystemTest extends TestCase {

	public static VirtualFileSystem vfs = null;

	public VirtualFileSystemTest(String arg0) {
		super(arg0);
	}

	private void recursiveList(VirtualFileSystemDirectory root) {
		for (String name : root.getFiles()) {
			VirtualFileSystemInode inode;
			try {
				inode = root.getInodeByName(name);
			} catch (FileNotFoundException e) {
				throw new RuntimeException("this cannot happen", e);
			}
			if (inode.isDirectory()) {
				recursiveList((VirtualFileSystemDirectory) inode);
			}
			System.out.println(inode.getPath());
		}
	}

	protected void setUp() {
		vfs = VirtualFileSystem.getVirtualFileSystem();
		try {
			vfs.getRoot().createDirectory("Test", "drftpd", "drftpd");
		} catch (FileExistsException e) {
		}
		try {
			vfs.getRoot().createFile("testFile", "drftpd", "drftpd");
		} catch (FileExistsException e) {
		}
	}

	protected void tearDown() throws Exception {
	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.deleteXML(String)'
	 */
	public void testDeleteXML() {

	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.getInodeByPath(String)'
	 */
	public void testGetInodeByPath() {

	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.getLast(String)'
	 */
	public void testGetLast() {

	}

	public void testList() {
		System.out.println("Starting testList");
		recursiveList(vfs.getRoot());
		System.out.println("Ending testList");
	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.loadInode(String)'
	 */
	public void testLoadInode() {

	}

	public void testMemory() {
		System.out
				.println("Stress test, we can scale now with lots of small files");
		for (int x = 0; x < 10; x++) {
			VirtualFileSystemDirectory walker1 = null;
			try {
				vfs.getRoot()
						.createDirectory(String.valueOf(x), "test", "test");
			} catch (FileExistsException e) {
			}
			try {
				walker1 = (VirtualFileSystemDirectory) vfs.getRoot()
						.getInodeByName(String.valueOf(x));
			} catch (FileNotFoundException e) {
				throw new RuntimeException("this can't be good1");
			}
			for (int y = 0; y < 10; y++) {
				VirtualFileSystemDirectory walker2 = null;
				try {
					walker1
							.createDirectory(String.valueOf(y), "test2",
									"test2");
				} catch (FileExistsException e) {
				}
				try {
					walker2 = (VirtualFileSystemDirectory) walker1
							.getInodeByName(String.valueOf(y));
				} catch (FileNotFoundException e) {
					throw new RuntimeException("this can't be good2");
				}
				for (int z = 0; z < 10; z++) {
					try {
						walker2.createFile(String.valueOf(z), "test3", "test3");
					} catch (FileExistsException e) {
					}
				}
			}
		}
		System.out.println("Done testing memory");
	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.renameXML(String,
	 * String)'
	 */
	public void testRenameXML() {

	}

	/*
	 * Test method for 'org.drftpd.vfs.VirtualFileSystem.stripLast(String)'
	 */
	public void testStripLast() {

	}

	/*
	 * Test method for
	 * 'org.drftpd.vfs.VirtualFileSystem.writeInode(VirtualFileSystemInode)'
	 */
	public void testWriteInode() {

	}

}
