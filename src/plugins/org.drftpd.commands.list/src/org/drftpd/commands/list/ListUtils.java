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
package org.drftpd.commands.list;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandleInterface;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.InodeHandleInterface;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author mog
 * @version $Id$
 */
public class ListUtils {
	public static final String PADDING = "          ";

	public static ListElementsContainer list(DirectoryHandle dir, ListElementsContainer container) throws IOException {
		ArrayList<InodeHandle> tempFileList = new ArrayList<InodeHandle>(dir.getInodeHandles(container.getSession().getUserNull(container.getUser())));
		ArrayList<InodeHandleInterface> listFiles = container.getElements();
		ArrayList<String> fileTypes = container.getFileTypes();
		int numOnline = container.getNumOnline();
		int numTotal = container.getNumTotal();
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = ListUtils.class.getName()+".";

		for (InodeHandle element : tempFileList) {
			boolean offlineFilesEnabled = GlobalContext.getConfig().getMainProperties().getProperty("files.offline.enabled", "true").equals("true");
			
			if (offlineFilesEnabled && element.isFile()) {
				if (!((FileHandleInterface) element).isAvailable()) {
					ReplacerEnvironment env = new ReplacerEnvironment();
					env.add("ofilename", element.getName());
					String oFileName = container.getSession().jprintf(bundle, keyPrefix+"files.offline.filename", env, container.getUser());

					listFiles.add(new LightRemoteInode(oFileName, element.getUsername(), element.getGroup(), element.lastModified(), element.getSize()));
					numTotal++;
				}
				// -OFFLINE and "ONLINE" files will both be present until someone implements
				// a way to reupload OFFLINE files.
				// It could be confusing to the user and/or client if the file doesn't exist, but you can't upload it. 
			}

			if (element.isFile()) {
				//else element is a file, and is online
				int typePosition = element.getName().lastIndexOf(".");
				String fileType;
				if (typePosition != -1) {
					fileType = element.getName().substring(typePosition, element.getName().length());
					if (!fileTypes.contains(fileType)) {
						fileTypes.add(fileType);
					}
				}
			}
			numOnline++;
			numTotal++;
			listFiles.add(element);
		}

		container.setNumOnline(numOnline);
		container.setNumTotal(numTotal);
		return container;
	}

	public static String padToLength(String value, int length) {
		if (value.length() >= length) {
			return value;
		}

		if (PADDING.length() < length) {
			throw new RuntimeException("padding must be longer than length");
		}

		return PADDING.substring(0, length - value.length()) + value;
	}
}
