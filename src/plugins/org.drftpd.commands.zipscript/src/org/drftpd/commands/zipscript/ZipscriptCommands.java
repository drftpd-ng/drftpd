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
package org.drftpd.commands.zipscript;

import java.io.FileNotFoundException;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.Checksum;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.Session;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptCommands extends CommandInterface implements EventSubscriber {

	private static final Logger logger = Logger.getLogger(ZipscriptCommands.class);

	private ArrayList<RescanPostProcessDirInterface> _rescanAddons = new ArrayList<RescanPostProcessDirInterface>();

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);

		GlobalContext.getEventService().subscribe(LoadPluginEvent.class, this);
		GlobalContext.getEventService().subscribe(UnloadPluginEvent.class, this);

		// load extensions just once and save the instances for later use.
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint rescanExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"org.drftpd.commands.zipscript", "RescanPostProcessDir");
		for (Extension rescanExt : rescanExtPoint.getConnectedExtensions()) {
			try {
				manager.activatePlugin(rescanExt.getDeclaringPluginDescriptor().getId());
				ClassLoader rescanLoader = manager.getPluginClassLoader( 
						rescanExt.getDeclaringPluginDescriptor());
				Class<?> rescanCls = rescanLoader.loadClass( 
							rescanExt.getParameter("Class").valueAsString());
				RescanPostProcessDirInterface rescanAddon = (RescanPostProcessDirInterface) rescanCls.newInstance();
				rescanAddon.initialize(cManager);
				_rescanAddons.add(rescanAddon);
			}
			catch (PluginLifecycleException e) {
				logger.debug("plugin lifecycle exception", e);
			}
			catch (ClassNotFoundException e) {
				logger.debug("bad plugin.xml or badly installed plugin: "+
						rescanExt.getDeclaringPluginDescriptor().getId(),e);
			}
			catch (Exception e) {
				logger.debug("failed to load class for rescan extension from: "+
						rescanExt.getDeclaringPluginDescriptor().getId(),e);
			}
		}
	}

	public CommandResponse doSITE_RESCAN(CommandRequest request) throws ImproperUsageException {

		Session session = request.getSession();
		boolean recursive = false;
		boolean forceRescan = false;
		boolean deleteBad = false;
		boolean quiet = false;
		StringTokenizer args = new StringTokenizer(request.getArgument());
		while (args.hasMoreTokens()) {
			String arg = args.nextToken();
			if (arg.equalsIgnoreCase("-r")) {
				recursive = true;
			} else if (arg.equalsIgnoreCase("force")) {
				forceRescan = true;
			} else if (arg.equalsIgnoreCase("delete")) {
				deleteBad = true;
			} else if (arg.equalsIgnoreCase("quiet")) {
				quiet = true;
			} else {
				throw new ImproperUsageException();
			}
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		LinkedList<DirectoryHandle> dirs = new LinkedList<DirectoryHandle>();
		dirs.add(request.getCurrentDirectory());
		while (dirs.size() > 0) {
			DirectoryHandle workingDir = dirs.poll();
			SFVInfo workingSfv;
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDir);
			try {
				workingSfv = sfvData.getSFVInfo();
			} catch (FileNotFoundException e2) {
				/* No sfv in this dir, silently ignore so not to add 
				 * useless output in recursive mode
				 */
				continue;
			} catch (NoAvailableSlaveException e2) {
				session.printOutput(200,"No available slave with sfv for: "+workingDir.getPath());
				continue;
			}
			session.printOutput(200,"Rescanning: "+workingDir.getPath());
			if (recursive) {
				try {
					dirs.addAll(workingDir.getDirectories(request.getSession().getUserNull(request.getUser())));
				} catch (FileNotFoundException e1) {
					session.printOutput(200,"Error recursively listing: "+workingDir.getPath());
				}
			}
			for (Entry<String,Long> sfvEntry : workingSfv.getEntries().entrySet()) {
				FileHandle file = new FileHandle(workingDir.getPath()
						+VirtualFileSystem.separator+sfvEntry.getKey());
				Long sfvChecksum = sfvEntry.getValue();
				Long fileChecksum = 0L;
				String status;
				try {
					if (forceRescan) {
						fileChecksum = file.getCheckSumFromSlave();
					} else {
						fileChecksum = file.getCheckSum();
					}
				} catch (FileNotFoundException e3) {
					session.printOutput(200,"SFV: " + Checksum.formatChecksum(sfvChecksum) + 
							" SLAVE: " + file.getName() + " MISSING");
					continue;
				} catch (NoAvailableSlaveException e3) {
					session.printOutput(200,"SFV: " + Checksum.formatChecksum(sfvChecksum) + 
							" SLAVE: " + file.getName() + " OFFLINE");
					continue;
				}
				if (fileChecksum == 0L) {
					status = "FAILED - failed to checksum file";
				} else if (sfvChecksum.longValue() == fileChecksum.longValue()) {
					if (quiet) {
						status = "";
					} else {
						status = "OK";
					}
				} else {
					status = "FAILED - checksum mismatch";
					if (deleteBad) {
						try {
							/* TODO if the user is rescanning and cannot delete the file
							 * what's the real point of rescanning? correct me if i'm wrong (fr0w) */
							file.deleteUnchecked();
						} catch (FileNotFoundException e4) {
							// File already gone, all is good
						}
					}
				}
				if (!status.equals("")) {
					session.printOutput(200,file.getName() + " SFV: " +
							Checksum.formatChecksum(sfvChecksum) + " SLAVE: " +
							Checksum.formatChecksum(fileChecksum) + " " + status);
				}
			}
			// Run any post processing extensions
			for (RescanPostProcessDirInterface rescanAddon: _rescanAddons) {
				CommandRequest workingDirReq = (CommandRequest) request.clone();
				workingDirReq.setCurrentDirectory(workingDir);
				rescanAddon.postProcessDir(workingDirReq);
			}
		}
		return response;
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
				if (pluginName.equals(currentPlugin) && extension.equals("RescanPostProcessDir")) {
					for (Iterator<RescanPostProcessDirInterface> iter = _rescanAddons.iterator(); iter.hasNext();) {
						RescanPostProcessDirInterface plugin = iter.next();
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
				if (pluginName.equals(currentPlugin) && extension.equals("RescanPostProcessDir")) {
					ExtensionPoint pluginExtPoint = 
						manager.getRegistry().getExtensionPoint( 
								"org.drftpd.commands.zipscript", "RescanPostProcessDir");
					for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
						if (plugin.getDeclaringPluginDescriptor().getId().equals(pluginEvent.getPlugin())) {
							try {
								manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
								ClassLoader pluginLoader = manager.getPluginClassLoader( 
										plugin.getDeclaringPluginDescriptor());
								Class<?> pluginCls = pluginLoader.loadClass( 
										plugin.getParameter("Class").valueAsString());
								RescanPostProcessDirInterface newPlugin = (RescanPostProcessDirInterface) pluginCls.newInstance();
								_rescanAddons.add(newPlugin);
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
