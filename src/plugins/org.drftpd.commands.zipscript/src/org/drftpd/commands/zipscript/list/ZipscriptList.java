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
package org.drftpd.commands.zipscript.list;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptList extends SFVTools implements AddListElementsInterface {

	private static final Logger logger = LogManager.getLogger(ZipscriptList.class);

	private ArrayList<ZipscriptListStatusBarInterface> _statusBarProviders = new ArrayList<>();

	public void initialize() {
		// Subscribe to events
		AnnotationProcessor.process(this);

		// Load any additional status bar element providers from plugins
		try {
			List<ZipscriptListStatusBarInterface> loadedStatusBarAddons =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.commands.zipscript", "ListStatusBarProvider", "Class");
			for (ZipscriptListStatusBarInterface sbAddon : loadedStatusBarAddons) {
				_statusBarProviders.add(sbAddon);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.zipscript extension point 'ListStatusBarProvider'"+
					", possibly the org.drftpd.commands.zipscript extension point definition has changed in the plugin.xml",e);
		}
	}

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";
		// Check config
		boolean statusBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
		boolean missingFilesEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("files.missing.enabled", "false").equalsIgnoreCase("true");
		if (statusBarEnabled || missingFilesEnabled) {
			ArrayList<String> statusBarEntries = new ArrayList<>();
			ReplacerEnvironment env = new ReplacerEnvironment();
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
				SFVInfo sfvfile = sfvData.getSFVInfo();
				SFVStatus sfvstatus = sfvData.getSFVStatus();

				if (statusBarEnabled) {
					if (sfvfile.getSize() != 0) {
						env.add("complete.total", "" + sfvfile.getSize());
						env.add("complete.number", "" + sfvstatus.getPresent());
						env.add("complete.percent", "" + (sfvstatus.getPresent() * 100)
								/ sfvfile.getSize());
						env.add("complete.totalbytes", Bytes.formatBytes(getSFVTotalBytes(dir, sfvData)));
						statusBarEntries.add(container.getSession().jprintf(bundle,
								keyPrefix+"statusbar.complete", env, container.getUser()));

						if (sfvstatus.getOffline() != 0) {
							env.add("offline.number","" + sfvstatus.getOffline());
							env.add("offline.percent",""+ (sfvstatus.getOffline() * 100) / sfvstatus.getPresent());
							env.add("online.number","" + sfvstatus.getPresent());
							env.add("online.percent","" + (sfvstatus.getAvailable() * 100) / sfvstatus.getPresent());
							statusBarEntries.add(container.getSession().jprintf(bundle,
									keyPrefix+"statusbar.offline",env,container.getUser()));
						}
					}
				}
				if (missingFilesEnabled && sfvfile.getSize() != 0) {
					for (String fileName : sfvfile.getEntries().keySet()) {
						FileHandle file = new FileHandle(dir.getPath()+VirtualFileSystem.separator+fileName);
						if (!file.exists()) {
							env.add("mfilename",fileName);
							container.getElements().add(new LightRemoteInode(
									container.getSession().jprintf(bundle, keyPrefix+"files.missing.filename",env,container.getUser()),
									"drftpd", "drftpd", dir.lastModified(), 0L));
						}
					}
				}
			} catch (NoAvailableSlaveException e) {
                logger.warn("No available slaves for SFV file in{}", dir.getPath());
			} catch (FileNotFoundException e) {
				// no sfv file in directory - just skip it
			} catch (IOException e) {
				// unable to read sfv - just skip it
			} catch (SlaveUnavailableException e) {
                logger.warn("No available slaves for SFV file in{}", dir.getPath());
			}
			if (statusBarEnabled) {
				for (ZipscriptListStatusBarInterface zle : _statusBarProviders) {
					try {
						for (String statusEntry : zle.getStatusBarEntry(dir,container)) {
							statusBarEntries.add(statusEntry);
						}
					} catch (NoEntryAvailableException e) {
						// Nothing to add at this time, carry on
					}
				}
				String entrySeparator = container.getSession().jprintf(bundle,
						keyPrefix+"statusbar.separator",env, container.getUser());
				StringBuilder statusBarBuilder = new StringBuilder();
				for (Iterator<String> iter = statusBarEntries.iterator();iter.hasNext();) {
					String statusBarElement = iter.next();
					statusBarBuilder.append(statusBarElement);
					if (iter.hasNext()) {
						statusBarBuilder.append(" ");
						statusBarBuilder.append(entrySeparator);
						statusBarBuilder.append(" ");
					}
				}
				if (statusBarBuilder.length() > 0) {
					env.add("statusbar",statusBarBuilder.toString());
					String statusDirName = container.getSession().jprintf(bundle,
							keyPrefix+"statusbar.format",env, container.getUser());

					if (statusDirName == null) {
						throw new RuntimeException();
					}

					try {
						boolean statusBarIsDir = GlobalContext.getGlobalContext().getPluginsConfig().
						getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.directory").equalsIgnoreCase("true");
						container.getElements().add(
								new LightRemoteInode(statusDirName, "drftpd", "drftpd", statusBarIsDir, dir.lastModified(), 0L));
					} catch (FileNotFoundException e) {
						// dir was deleted during list operation
					}
				}
			}
		}
		return container;
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		Set<ZipscriptListStatusBarInterface> unloadedStatusBarAddons =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "ListStatusBarProviders", event, _statusBarProviders);
		if (!unloadedStatusBarAddons.isEmpty()) {
			ArrayList<ZipscriptListStatusBarInterface> clonedProviders = new ArrayList<>(_statusBarProviders);
			boolean providerRemoved = false;
			for (Iterator<ZipscriptListStatusBarInterface> iter = _statusBarProviders.iterator(); iter.hasNext();) {
				ZipscriptListStatusBarInterface sbAddon = iter.next();
				if (unloadedStatusBarAddons.contains(sbAddon)) {
                    logger.debug("Unloading status bar provider addon provided by plugin {}", CommonPluginUtils.getPluginIdForObject(sbAddon));
					iter.remove();
					providerRemoved = true;
				}
			}
			if (providerRemoved) {
				_statusBarProviders = clonedProviders;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<ZipscriptListStatusBarInterface> loadedStatusBarAddons =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.commands.zipscript", "ListStatusBarProvider", "Class", event);
			if (!loadedStatusBarAddons.isEmpty()) {
				ArrayList<ZipscriptListStatusBarInterface> clonedProviders = new ArrayList<>(_statusBarProviders);
				for (ZipscriptListStatusBarInterface sbAddon : loadedStatusBarAddons) {
					clonedProviders.add(sbAddon);
				}
				_statusBarProviders = clonedProviders;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for a loadplugin event for org.drftpd.commands.zipscript extension point 'ListStatusBarProvider'"+
					", possibly the org.drftpd.commands.zipscript extension point definition has changed in the plugin.xml",e);
		}
	}

	public void unload() {
		AnnotationProcessor.unprocess(this);
	}
}
