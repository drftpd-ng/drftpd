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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptList extends SFVTools implements AddListElementsInterface, EventSubscriber {

	private static final Logger logger = Logger.getLogger(ZipscriptList.class);

	private ArrayList<ZipscriptListStatusBarInterface> _statusBarProviders = new ArrayList<ZipscriptListStatusBarInterface>();

	public void initialize() {
		GlobalContext.getEventService().subscribe(LoadPluginEvent.class, this);
		GlobalContext.getEventService().subscribe(UnloadPluginEvent.class, this);

		// load extensions just once and save the instances for later use.
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint sbExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"org.drftpd.commands.zipscript", "ListStatusBarProvider");
		for (Extension sbExt : sbExtPoint.getConnectedExtensions()) {
			try {
				manager.activatePlugin(sbExt.getDeclaringPluginDescriptor().getId());
				ClassLoader sbLoader = manager.getPluginClassLoader( 
						sbExt.getDeclaringPluginDescriptor());
				Class<?> sbCls = sbLoader.loadClass( 
						sbExt.getParameter("Class").valueAsString());
				ZipscriptListStatusBarInterface sbAddon = (ZipscriptListStatusBarInterface) sbCls.newInstance();
				_statusBarProviders.add(sbAddon);
			}
			catch (PluginLifecycleException e) {
				logger.debug("plugin lifecycle exception", e);
			}
			catch (ClassNotFoundException e) {
				logger.debug("bad plugin.xml or badly installed plugin: "+
						sbExt.getDeclaringPluginDescriptor().getId(),e);
			}
			catch (Exception e) {
				logger.debug("failed to load class for list extension from: "+
						sbExt.getDeclaringPluginDescriptor().getId(),e);
			}
		}
	}

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {

		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";
		// Check config
		boolean statusBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.enabled").equalsIgnoreCase("true");
		boolean missingFilesEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("files.missing.enabled").equalsIgnoreCase("true");
		if (statusBarEnabled || missingFilesEnabled) {
			ArrayList<String> statusBarEntries = new ArrayList<String>();
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
				logger.warn("No available slaves for SFV file", e);
			} catch (FileNotFoundException e) {
				// no sfv file in directory - just skip it
			} catch (IOException e) {
				// unable to read sfv - just skip it
			} catch (SlaveUnavailableException e) {
				logger.warn("No available slaves for SFV file", e);
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
					String statusDirName = null;
					statusDirName = container.getSession().jprintf(bundle,
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

	public void onEvent(Object event) {
		if (event instanceof UnloadPluginEvent) {
			UnloadPluginEvent pluginEvent = (UnloadPluginEvent) event;
			PluginManager manager = PluginManager.lookup(this);
			String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
			for (String pluginExtension : pluginEvent.getParentPlugins()) {
				int pointIndex = pluginExtension.lastIndexOf("@");
				String pluginName = pluginExtension.substring(0, pointIndex);
				String extension = pluginExtension.substring(pointIndex+1);
				if (pluginName.equals(currentPlugin) && extension.equals("ListStatusBarProvider")) {
					for (Iterator<ZipscriptListStatusBarInterface> iter = _statusBarProviders.iterator(); iter.hasNext();) {
						ZipscriptListStatusBarInterface plugin = iter.next();
						if (manager.getPluginFor(plugin).getDescriptor().getId().equals(pluginEvent.getPlugin())) {
							logger.debug("Unloading plugin "+manager.getPluginFor(plugin).getDescriptor().getId());
							iter.remove();
						}
					}
				}
			}
		} else if (event instanceof LoadPluginEvent) {
			LoadPluginEvent pluginEvent = (LoadPluginEvent) event;
			PluginManager manager = PluginManager.lookup(this);
			String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
			for (String pluginExtension : pluginEvent.getParentPlugins()) {
				int pointIndex = pluginExtension.lastIndexOf("@");
				String pluginName = pluginExtension.substring(0, pointIndex);
				String extension = pluginExtension.substring(pointIndex+1);
				if (pluginName.equals(currentPlugin) && extension.equals("ListStatusBarProvider")) {
					ExtensionPoint pluginExtPoint = 
						manager.getRegistry().getExtensionPoint( 
								"org.drftpd.commands.zipscript", "ListStatusBarProvider");
					for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
						if (plugin.getDeclaringPluginDescriptor().getId().equals(pluginEvent.getPlugin())) {
							try {
								manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
								ClassLoader pluginLoader = manager.getPluginClassLoader( 
										plugin.getDeclaringPluginDescriptor());
								Class<?> pluginCls = pluginLoader.loadClass( 
										plugin.getParameter("Class").valueAsString());
								ZipscriptListStatusBarInterface newPlugin = (ZipscriptListStatusBarInterface) pluginCls.newInstance();
								_statusBarProviders.add(newPlugin);
							}
							catch (Exception e) {
								logger.warn("Error loading plugin " + 
										plugin.getDeclaringPluginDescriptor().getId(),e);
							}
						}
					}
				}
			}
		}
	}
}
