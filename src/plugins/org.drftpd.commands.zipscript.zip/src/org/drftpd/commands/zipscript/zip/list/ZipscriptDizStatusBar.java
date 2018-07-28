/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands.zipscript.zip.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.zipscript.list.NoEntryAvailableException;
import org.drftpd.commands.zipscript.list.ZipscriptListStatusBarInterface;
import org.drftpd.commands.zipscript.zip.ZipTools;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptDizStatusBar extends ZipTools implements ZipscriptListStatusBarInterface {

	public ArrayList<String> getStatusBarEntry(DirectoryHandle dir,ListElementsContainer container) throws NoEntryAvailableException {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";
		// Check config
		boolean statusBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
		if (statusBarEnabled) {
			try {
				ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);
				DizInfo dizInfo = zipData.getDizInfo();
				DizStatus dizStatus = zipData.getDizStatus();
				ReplacerEnvironment env = new ReplacerEnvironment();

				ArrayList<String> statusBarEntries = new ArrayList<>();
				if (dizInfo.getTotal() != 0) {
					env.add("complete.total", "" + dizInfo.getTotal());
					env.add("complete.number", "" + dizStatus.getPresent());
					env.add("complete.percent", "" + (dizStatus.getPresent() * 100)
							/ dizInfo.getTotal());
					env.add("complete.totalbytes", Bytes.formatBytes(getZipTotalBytes(dir)));
					statusBarEntries.add(container.getSession().jprintf(bundle,
							keyPrefix+"statusbar.complete", env, container.getUser()));

					if (dizStatus.getOffline() != 0) {
						env.add("offline.number","" + dizStatus.getOffline());
						env.add("offline.percent",""+ (dizStatus.getOffline() * 100) / dizStatus.getPresent());
						env.add("online.number","" + dizStatus.getPresent());
						env.add("online.percent","" + (dizStatus.getAvailable() * 100) / dizStatus.getPresent());
						statusBarEntries.add(container.getSession().jprintf(bundle,
								keyPrefix+"statusbar.offline",env,container.getUser()));
					}
					return statusBarEntries;
				}
			} catch (FileNotFoundException e) {
				// Error fetching diz info, ignore
			} catch (IOException e) {
				// Error fetching diz info, ignore
			} catch (NoAvailableSlaveException e) {
				// Error fetching diz info, ignore
			}
		}
		throw new NoEntryAvailableException();
	}
}
