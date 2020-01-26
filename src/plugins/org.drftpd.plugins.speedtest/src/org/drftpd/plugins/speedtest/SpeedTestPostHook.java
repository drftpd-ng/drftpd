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
package org.drftpd.plugins.speedtest;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.speedtest.event.SpeedTestEvent;
import org.drftpd.slave.TransferStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Properties;

/**
 * @author scitz0
 */
public class SpeedTestPostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(SpeedTestPostHook.class);

	private ArrayList<String> _speedTestPaths = new ArrayList<>();

	public void initialize(StandardCommandManager manager) {
		loadConf();
	}

	private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("speedtest.conf");
		if (cfg == null) {
			logger.error("conf/plugins/speedtest.conf not found");
			return;
		}
		_speedTestPaths.clear();
		for (int i = 1;; i++) {
			String path = cfg.getProperty("path."+i);
			if (path == null) break;
			_speedTestPaths.add(VirtualFileSystem.fixPath(path));
		}
	}

	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
		DirectoryHandle dir = request.getCurrentDirectory();

		// Check if STOR was made in a speedtest path
		for (String stPath : _speedTestPaths) {
			if (dir.getPath().startsWith(stPath)) {
				FileHandle file = response.getObject(DataConnectionHandler.TRANSFER_FILE, null);
				TransferStatus status = response.getObject(DataConnectionHandler.XFER_STATUS, null);
				RemoteSlave rslave = response.getObject(DataConnectionHandler.TRANSFER_SLAVE, null);

				if (response.getCode() == 226 && file != null && status != null && rslave != null) {
					User user;
					try {
						user = request.getUserObject();
					} catch (NoSuchUserException e) {
						// Unable to get user sending file, log and return.
						logger.warn("Unable to get user sending file", e);
						return;
					} catch (UserFileException e) {
						// Failed getting user, log and return.
						logger.error(e);
						return;
					}
					GlobalContext.getEventService().publishAsync(new SpeedTestEvent
							(file.getPath(), rslave.getName(), status, user));
				}
				// Remove file regardless if STOR failed or not if still there
				if (file != null) {
					try {
						file.deleteUnchecked();
					} catch (FileNotFoundException e) {
						// Deleted already
					}
				}
				return;
			}
		}
	}
}
