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
package org.drftpd.sections.conf;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author mog
 * @version $Id$
 */
public class PlainSection implements ConfigurableSectionInterface {
	private static Logger logger = Logger.getLogger(PlainSection.class);

	private String _name;
	
	protected DirectoryHandle _basePath;
	
	public PlainSection(int i, Properties p) {
		this(PropertyHelper.getProperty(p, i + ".name"), new DirectoryHandle(PropertyHelper.getProperty(p, i+ ".path")));
	}
	
	public PlainSection(String name, DirectoryHandle dir) {
		_name = name;
		_basePath = dir;
	}
	
	public String getName() {
		return _name;
	}

	public DirectoryHandle getBaseDirectory() {
		return _basePath;
	}
	
	public String getBasePath() {
		return _basePath.getPath();
	}
	
	public DirectoryHandle getCurrentDirectory() {
		return getBaseDirectory();
	}


	@SuppressWarnings("unchecked")
	public Set<DirectoryHandle> getDirectories() {
		try {
			return getBaseDirectory().getDirectoriesUnchecked();
		} catch (FileNotFoundException e) {
			return Collections.EMPTY_SET;
		}
	}
	
	protected static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public void createSectionDir() {
		try {
			String path = getCurrentDirectory().getPath();
			DirectoryHandle dir = new DirectoryHandle(VirtualFileSystem.stripLast(path));		
			dir.createDirectoryRecursive(VirtualFileSystem.getLast(path));
		} catch (FileExistsException e) {
			// good the file exists, no need to create it.
		} catch (FileNotFoundException e) {
			logger.error("What happened? I don't know how to handle this", e);
		}
	}
}
