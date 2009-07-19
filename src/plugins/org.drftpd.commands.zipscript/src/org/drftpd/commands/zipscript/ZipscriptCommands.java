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
import java.io.IOException;
import java.util.Map.Entry;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Checksum;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.Session;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptCommands extends CommandInterface {

	private static final Logger logger = Logger.getLogger(ZipscriptCommands.class);

	private ArrayList<RescanPostProcessDirInterface> _rescanAddons = new ArrayList<RescanPostProcessDirInterface>();

	private StandardCommandManager _commandManager;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_commandManager = cManager;

		// Subscribe to events
		AnnotationProcessor.process(this);

		// Load any rescan post process providers from plugins
		try {
			List<RescanPostProcessDirInterface> loadedRescanAddons =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.commands.zipscript", "RescanPostProcessDir", "Class");
			for (RescanPostProcessDirInterface rescanAddon : loadedRescanAddons) {
				rescanAddon.initialize(_commandManager);
				_rescanAddons.add(rescanAddon);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.zipscript extension point 'RescanPostProcessDir'"+
					", possibly the org.drftpd.commands.zipscript extension point definition has changed in the plugin.xml",e);
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
			} catch (IOException e2) {
				/* Unable to read sfv in this dir, silently ignore so not to add 
				 * useless output in recursive mode
				 */
				continue;
			} catch (NoAvailableSlaveException e2) {
				session.printOutput(200,"No available slave with sfv for: "+workingDir.getPath());
				continue;
			} catch (SlaveUnavailableException e2) {
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

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		Set<RescanPostProcessDirInterface> unloadedRescanAddons =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "RescanPostProcessDir", event, _rescanAddons);
		if (!unloadedRescanAddons.isEmpty()) {
			ArrayList<RescanPostProcessDirInterface> clonedRescanAddons = new ArrayList<RescanPostProcessDirInterface>(_rescanAddons);
			boolean addonRemoved = false;
			for (Iterator<RescanPostProcessDirInterface> iter = clonedRescanAddons.iterator(); iter.hasNext();) {
				RescanPostProcessDirInterface rescanAddon = iter.next();
				if (unloadedRescanAddons.contains(rescanAddon)) {
					logger.debug("Unloading rescan post process addon provided by plugin "
							+CommonPluginUtils.getPluginIdForObject(rescanAddon));
					iter.remove();
					addonRemoved = true;
				}
			}
			if (addonRemoved) {
				_rescanAddons = clonedRescanAddons;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<RescanPostProcessDirInterface> loadedRescanAddons =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.commands.zipscript", "RescanPostProcessDir", "Class", event);
			if (!loadedRescanAddons.isEmpty()) {
				ArrayList<RescanPostProcessDirInterface> clonedRescanAddons = new ArrayList<RescanPostProcessDirInterface>(_rescanAddons);
				for (RescanPostProcessDirInterface rescanAddon : loadedRescanAddons) {
					rescanAddon.initialize(_commandManager);
					clonedRescanAddons.add(rescanAddon);
				}
				_rescanAddons = clonedRescanAddons;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.zipscript extension point 'RescanPostProcessDir'"+
					", possibly the org.drftpd.commands.zipscript extension point definition has changed in the plugin.xml",e);
		}
	}
}
