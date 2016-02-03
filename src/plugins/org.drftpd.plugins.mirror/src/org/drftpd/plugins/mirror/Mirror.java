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
package org.drftpd.plugins.mirror;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.ReloadEvent;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * @author lh
 */
public class Mirror extends CommandInterface {
	private static final Logger logger = Logger.getLogger(Mirror.class);
	private ResourceBundle _bundle;
	private String _keyPrefix;
	private ArrayList<String> _excludePaths;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName() + ".";
		_excludePaths = new ArrayList<String>();
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("mirror.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/mirror.conf not found");
			return;
		}
		_excludePaths.clear();
		for (int i = 1;; i++) {
			String excludePath = cfg.getProperty(i + ".unmirrorExclude");
			if (excludePath == null) break;
			_excludePaths.add(excludePath);
		}
	}

	public CommandResponse doSITE_UNMIRROR(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		DirectoryHandle dir;
		try {
			dir = GlobalContext.getGlobalContext().getRoot().getDirectory(
					request.getArgument(), request.getUserObject());
		} catch (Exception e) {
			return new CommandResponse(500, "Failed getting requested directory: " + e.getMessage());
		}
		try {
			unMirrorDir(dir, request.getUserObject());
		} catch (Exception e) {
			return new CommandResponse(500, "Unmirror error: " + e.getMessage());
		}
		return new CommandResponse(200, "Directory successfully unmirrored!");
	}

	private void unMirrorDir(DirectoryHandle dir, User user) throws FileNotFoundException {
		for (String excludePath : _excludePaths) {
			if (dir.getPath().matches(excludePath)) {
				// Skip this dir
				return;
			}
		}
		for (InodeHandle inode : dir.getInodeHandles(user)) {
			if (inode.isFile()) {
				boolean skipFile = false;
				for (String excludePath : _excludePaths) {
					if (inode.getPath().matches(excludePath)) {
						// Skip this file
						skipFile = true;
						break;
					}
				}
				if (!skipFile) unMirrorFile((FileHandle) inode);
			} else if (inode.isDirectory()) {
				unMirrorDir((DirectoryHandle) inode, user);
			}
		}
	}

	private void unMirrorFile(FileHandle file) {
		try {
			boolean first = true;
			for (RemoteSlave slave : file.getSlaves()) {
				if (first) {
					first = false;
				} else {
					slave.simpleDelete(file.getPath());
					file.removeSlave(slave);
				}
			}
		} catch (FileNotFoundException e) {
			// Just ignore, file doesn't exist any more
		}
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
}
