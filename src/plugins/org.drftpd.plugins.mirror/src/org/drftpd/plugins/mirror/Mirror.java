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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.event.ReloadEvent;
import org.drftpd.vfs.DirectoryHandle;

import java.util.ArrayList;
import java.util.Properties;

/**
 * @author lh
 */
public class Mirror extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(Mirror.class);
	private ArrayList<String> _excludePaths;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_excludePaths = new ArrayList<>();
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
			MirrorUtils.unMirrorDir(dir, request.getUserObject(), _excludePaths);
		} catch (Exception e) {
			return new CommandResponse(500, "Unmirror error: " + e.getMessage());
		}
		return new CommandResponse(200, "Directory successfully unmirrored!");
	}



	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
}
