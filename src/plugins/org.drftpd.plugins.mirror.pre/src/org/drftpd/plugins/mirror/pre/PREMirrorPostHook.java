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
package org.drftpd.plugins.mirror.pre;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.pre.Pre;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.plugins.mirror.MirrorUtils;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;

/**
 * @author lh
 */
public class PREMirrorPostHook implements PostHookInterface {
	private static final Logger logger = LogManager.getLogger(PREMirrorPostHook.class);
	private long _unmirrorTime;
	private ArrayList<String> _excludePaths;
	private Timer _preTimer;

	public void initialize(StandardCommandManager manager) {
		_excludePaths = new ArrayList<>();
		_preTimer = new Timer();
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
		_unmirrorTime = Long.parseLong(cfg.getProperty("pre.unmirror.time", "60"));
		if (_unmirrorTime != 0L) {
			_unmirrorTime = _unmirrorTime * 1000 * 60;
		}
		_excludePaths.clear();
		for (int i = 1;; i++) {
			String excludePath = cfg.getProperty(i + ".unmirrorExclude");
			if (excludePath == null) break;
			_excludePaths.add(excludePath);
		}
	}

	public void doPREPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// PRE failed, abort
			return;
		}

		if (_unmirrorTime == 0L) return;

		try {
			PRETask preTask = new PRETask(response.getObject(Pre.PREDIR));
			_preTimer.schedule(preTask, _unmirrorTime);
		} catch (KeyNotFoundException e) {
			// Pre dir not set? Ignore and exit
		}
	}

	private class PRETask extends TimerTask {
		private DirectoryHandle dir;

		public PRETask(DirectoryHandle dir) {
			this.dir = dir;
		}

		public void run() {
			try {
				MirrorUtils.unMirrorDir(dir, null, _excludePaths);
			} catch (FileNotFoundException e) {
                logger.error("Unmirror error: {}", e.getMessage());
			}
		}
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}

	@EventSubscriber
	public void onUnloadPluginEvent(UnloadPluginEvent event) {
		String currentPlugin = CommonPluginUtils.getPluginIdForObject(this);
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			if (pluginName.equals(currentPlugin)) {
				AnnotationProcessor.unprocess(this);
				logger.info("Cancelling timer all active unmirror tasks");
				_preTimer.cancel();
				return;
			}
		}
	}
}
