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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Checksum;
import org.drftpd.commandmanager.*;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.Session;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.ObjectNotValidException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

//import org.drftpd.slave.async.AsyncResponseSiteBotMessage;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptCommands extends CommandInterface {

	private static final Logger logger = LogManager.getLogger(ZipscriptCommands.class);

	private ArrayList<RescanPostProcessDirInterface> _rescanAddons = new ArrayList<>();

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
		User user = session.getUserNull(request.getUser());
		String startPath = null;
		boolean recursive = false;
		boolean forceRescan = true;
		boolean deleteBad = true;
		boolean deleteZeroByte = true;
		boolean quiet = false;
		StringTokenizer args = new StringTokenizer(request.getArgument());
		while (args.hasMoreTokens()) {
			String arg = args.nextToken();
			if (arg.equalsIgnoreCase("-r")) {
				recursive = true;
			} else if (arg.equalsIgnoreCase("noforce")) {
				forceRescan = false;
			} else if (arg.equalsIgnoreCase("nodelete")) {
				deleteBad = false;
			} else if (arg.equalsIgnoreCase("nodelete0byte")) {
				deleteZeroByte = false;
			} else if (arg.equalsIgnoreCase("quiet")) {
				quiet = true;
			} else if (arg.startsWith("/")) {
				startPath = arg;
			} else {
				throw new ImproperUsageException();
			}
		}
		
		if (!deleteBad && !deleteZeroByte)
		{
			forceRescan = false;
		}

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		LinkedList<DirectoryHandle> dirs = new LinkedList<>();
		if (startPath != null) {
			boolean validPath = false;
			try {
				if (InodeHandle.isDirectory(startPath)) {
					dirs.add(new DirectoryHandle(startPath));
					validPath = true;
				}
			} catch (FileNotFoundException e) {
				// Do nothing, the valid path check will deal with this
			}
			if (!validPath) {
				session.printOutput(startPath+" is not a valid path");
				throw new ImproperUsageException();
			}
		} else {
			dirs.add(request.getCurrentDirectory());
		}
		while (dirs.size() > 0 && !session.isAborted()) {
			DirectoryHandle workingDir = dirs.poll();
			if (recursive) {
				try {
					dirs.addAll(workingDir.getSortedDirectories(user));
				} catch (FileNotFoundException e1) {
					session.printOutput(200,"Error recursively listing: "+workingDir.getPath());
				}
			}
			SFVInfo workingSfv = null;
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDir);
			boolean sfvFound = false;
			try {
				workingSfv = sfvData.getSFVInfo();
				sfvFound = true;
			} catch (FileNotFoundException e2) {
				// Need to carry on anyway but skip sfv checking, this allows any extensions to run
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
			if (sfvFound) {
				for (Entry<String,Long> sfvEntry : workingSfv.getEntries().entrySet()) {
					if (session.isAborted()) {
						break;
					}
					FileHandle file = null;
					Long sfvChecksum = sfvEntry.getValue();
					String sfvEntryName = sfvEntry.getKey();
					Long fileChecksum = 0L;
					Long fileSize = 0L;
					String status;
					try {
						file = workingDir.getFile(sfvEntryName, user);
						fileSize = file.getSize();
						if (forceRescan) {
							fileChecksum = file.getCheckSumFromSlave();
						} else {
							fileChecksum = file.getCheckSum();
						}
					} catch (FileNotFoundException e3) {
						session.printOutput(200,"SFV: " + Checksum.formatChecksum(sfvChecksum) + 
								" SLAVE: " + sfvEntryName + " MISSING");
						continue;
					} catch (NoAvailableSlaveException e3) {
						session.printOutput(200,"SFV: " + Checksum.formatChecksum(sfvChecksum) + 
								" SLAVE: " + sfvEntryName + " OFFLINE");
						continue;
					} catch (ObjectNotValidException e3) {
						session.printOutput(200,"SFV: " + Checksum.formatChecksum(sfvChecksum) + 
								" SLAVE: " + sfvEntryName + " INVALID VFS ENTRY");
                        logger.error("Type error found in VFS, expected file {} and found something else", sfvEntryName, e3);
						continue;
					}
					if (fileChecksum == 0L) {
						if (fileSize == 0L) {
							status = "ZEROBYTE";
							if (deleteZeroByte) {
								try {
									file.deleteUnchecked();
									status += " - deleted";
								} catch (FileNotFoundException e4) {
									// File already gone, all is good
								}
							}
						} else {
							status = "FAILED - failed to checksum file";
						}
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
			}
			// Run any post processing extensions
			for (RescanPostProcessDirInterface rescanAddon: _rescanAddons) {
				CommandRequest workingDirReq = (CommandRequest) request.clone();
				workingDirReq.setCurrentDirectory(workingDir);
				rescanAddon.postProcessDir(workingDirReq, quiet);
			}
		}
		return response;
	}

	@EventSubscriber @Override
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		super.onUnloadPluginEvent(event);
		Set<RescanPostProcessDirInterface> unloadedRescanAddons =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "RescanPostProcessDir", event, _rescanAddons);
		if (!unloadedRescanAddons.isEmpty()) {
			ArrayList<RescanPostProcessDirInterface> clonedRescanAddons = new ArrayList<>(_rescanAddons);
			boolean addonRemoved = false;
			for (Iterator<RescanPostProcessDirInterface> iter = clonedRescanAddons.iterator(); iter.hasNext();) {
				RescanPostProcessDirInterface rescanAddon = iter.next();
				if (unloadedRescanAddons.contains(rescanAddon)) {
                    logger.debug("Unloading rescan post process addon provided by plugin {}", CommonPluginUtils.getPluginIdForObject(rescanAddon));
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
				ArrayList<RescanPostProcessDirInterface> clonedRescanAddons = new ArrayList<>(_rescanAddons);
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
