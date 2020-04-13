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
package org.drftpd.zipscript.master.zip.list;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.ConfigType;
import org.drftpd.master.commands.list.ListElementsContainer;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.sfv.list.NoEntryAvailableException;
import org.drftpd.zipscript.master.sfv.list.ZipscriptListStatusBarInterface;
import org.drftpd.zipscript.master.zip.ZipTools;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.vfs.DirectoryHandle;
import java.io.IOException;
import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptDizStatusBar extends ZipTools implements ZipscriptListStatusBarInterface {

	public ArrayList<String> getStatusBarEntry(DirectoryHandle dir, ListElementsContainer container) throws NoEntryAvailableException {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		// Check config
		Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf", ConfigType.MASTER);
		boolean statusBarEnabled = cfg.getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
		if (statusBarEnabled) {
			try {
				ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);
				DizInfo dizInfo = zipData.getDizInfo();
				DizStatus dizStatus = zipData.getDizStatus();
				Map<String, Object> env = new HashMap<>();

				ArrayList<String> statusBarEntries = new ArrayList<>();
				if (dizInfo.getTotal() != 0) {
					env.put("complete.total", "" + dizInfo.getTotal());
					env.put("complete.number", "" + dizStatus.getPresent());
					env.put("complete.percent", "" + (dizStatus.getPresent() * 100)
							/ dizInfo.getTotal());
					env.put("complete.totalbytes", Bytes.formatBytes(getZipTotalBytes(dir)));
					statusBarEntries.add(container.getSession().jprintf(bundle, "diz.statusbar.complete", env, container.getUser()));

					if (dizStatus.getOffline() != 0) {
						env.put("offline.number","" + dizStatus.getOffline());
						env.put("offline.percent",""+ (dizStatus.getOffline() * 100) / dizStatus.getPresent());
						env.put("online.number","" + dizStatus.getPresent());
						env.put("online.percent","" + (dizStatus.getAvailable() * 100) / dizStatus.getPresent());
						statusBarEntries.add(container.getSession().jprintf(bundle, "diz.statusbar.offline",env,container.getUser()));
					}
					return statusBarEntries;
				}
			} catch (IOException | NoAvailableSlaveException e) {
				// Error fetching diz info, ignore
			}
		}
		throw new NoEntryAvailableException();
	}
}
