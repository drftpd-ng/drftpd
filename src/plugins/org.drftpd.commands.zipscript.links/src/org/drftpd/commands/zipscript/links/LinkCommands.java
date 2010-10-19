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
package org.drftpd.commands.zipscript.links;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
import org.drftpd.vfs.ObjectNotValidException;

/**
 * @author djb61
 * @version $Id$
 */
public class LinkCommands extends CommandInterface {

	private static final Logger logger = Logger.getLogger(LinkCommands.class);

	private ArrayList<FixLinksProcessDirInterface> _fixLinksAddons = new ArrayList<FixLinksProcessDirInterface>();
	
	private ResourceBundle _bundle;
	
	private StandardCommandManager _commandManager;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		
		_commandManager = cManager;

		// Subscribe to events
		AnnotationProcessor.process(this);
		
		// Load any rescan post process providers from plugins
		try {
			List<FixLinksProcessDirInterface> loadedFixLinksAddons =
				CommonPluginUtils.getPluginObjects(this, "org.drftpd.commands.zipscript.links", "FixLinksProcessDir", "Class");
			for (FixLinksProcessDirInterface fixLinksAddon : loadedFixLinksAddons) {
				fixLinksAddon.initialize(_commandManager);
				_fixLinksAddons.add(fixLinksAddon);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.zipscript.links extension point 'FixLinksProcessDir'"+
					", possibly the org.drftpd.commands.zipscript.links extension point definition has changed in the plugin.xml",e);
		}	
	}

	public CommandResponse doSITE_FIXLINKS(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		LinkedList<DirectoryHandle> dirs = new LinkedList<DirectoryHandle>();
		User user = request.getSession().getUserNull(request.getUser());
		dirs.add(request.getCurrentDirectory());
		while (dirs.size() > 0) {
			DirectoryHandle workingDir = dirs.poll();
			try {
				for (LinkHandle link : workingDir.getLinks(user)) {
					try {
						link.getTargetDirectory(user).getPath();
					} catch (FileNotFoundException e1) {
						// Link target no longer exists, remote it
						link.deleteUnchecked();
					} catch (ObjectNotValidException e1) {
						// Link target isn't a directory, delete the link as it is bad
						link.deleteUnchecked();
						continue;
					}
				}
			} catch (FileNotFoundException e2) {
				logger.warn("Invalid link in dir " + workingDir.getPath(),e2);
			}
			try {
				dirs.addAll(workingDir.getDirectoriesUnchecked());
			}
			catch (FileNotFoundException e1) {
				response.addComment("Error recursively listing: "+workingDir.getPath());
			}
			Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
			getPropertiesForPlugin("zipscript.conf");
			// Check if incomplete links are enabled
			if (cfg.getProperty("incomplete.links", "false").equals("true")) {
				// check incomplete status and update links
				CommandRequest tempReq = (CommandRequest) request.clone();
				tempReq.setCurrentDirectory(workingDir);
				try {
					ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDir);
					SFVStatus sfvStatus = sfvData.getSFVStatus();
					if (sfvStatus.isFinished()) {
						// dir is complete, remove link if needed
						LinkUtils.processLink(tempReq, "delete", _bundle);
					}
					else {
						// dir is incomplete, add link if needed
						LinkUtils.processLink(tempReq, "create", _bundle);
					}
				} catch (NoAvailableSlaveException e) {
					// Slave holding sfv is unavailable
				} catch (FileNotFoundException e) {
					// No sfv in dir
				} catch (IOException e) {
					// sfv not readable
				} catch (SlaveUnavailableException e) {
					// Slave holding sfv is unavailable
				}
			}
			
			for (FixLinksProcessDirInterface fixLinksAddon: _fixLinksAddons) {
				CommandRequest workingDirReq = (CommandRequest) request.clone();
				workingDirReq.setCurrentDirectory(workingDir);
				workingDirReq.setUser(user.getName());
				fixLinksAddon.processDir(workingDirReq);
			}
			
		}
		return response;
	}
	
	@EventSubscriber @Override
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		super.onUnloadPluginEvent(event);
		Set<FixLinksProcessDirInterface> unloadedFixLinksAddons =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "FixLinksProcessDir", event, _fixLinksAddons);
		if (!unloadedFixLinksAddons.isEmpty()) {
			ArrayList<FixLinksProcessDirInterface> clonedFixLinksAddons = new ArrayList<FixLinksProcessDirInterface>(_fixLinksAddons);
			boolean addonRemoved = false;
			for (Iterator<FixLinksProcessDirInterface> iter = clonedFixLinksAddons.iterator(); iter.hasNext();) {
				FixLinksProcessDirInterface fixLinksAddon = iter.next();
				if (unloadedFixLinksAddons.contains(fixLinksAddon)) {
					logger.debug("Unloading fix links process addon provided by plugin "
							+CommonPluginUtils.getPluginIdForObject(fixLinksAddon));
					iter.remove();
					addonRemoved = true;
				}
			}
			if (addonRemoved) {
				_fixLinksAddons = clonedFixLinksAddons;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		try {
			List<FixLinksProcessDirInterface> loadedFixLinksAddons =
				MasterPluginUtils.getLoadedExtensionObjects(this, "org.drftpd.commands.zipscript.links", "FixLinksProcessDir", "Class", event);
			if (!loadedFixLinksAddons.isEmpty()) {
				ArrayList<FixLinksProcessDirInterface> clonedFixLinksAddons = new ArrayList<FixLinksProcessDirInterface>(_fixLinksAddons);
				for (FixLinksProcessDirInterface fixLinksAddon : loadedFixLinksAddons) {
					fixLinksAddon.initialize(_commandManager);
					clonedFixLinksAddons.add(fixLinksAddon);
				}
				_fixLinksAddons = clonedFixLinksAddons;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.commands.zipscript.links extension point 'FixLinksProcessDir'"+
					", possibly the org.drftpd.commands.zipscript.links extension point definition has changed in the plugin.xml",e);
		}
	}	
	
}
