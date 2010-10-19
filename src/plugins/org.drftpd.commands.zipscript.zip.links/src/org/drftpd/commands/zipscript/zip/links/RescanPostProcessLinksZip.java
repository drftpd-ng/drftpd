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
package org.drftpd.commands.zipscript.zip.links;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.links.LinkUtils;
import org.drftpd.commands.zipscript.RescanPostProcessDirInterface;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.commands.zipscript.zip.DizStatus;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;

/**
 * @author CyBeR
 * @version $Id: RescanPostProcessLinksZip.java 2129 2010-09-30 19:27:50Z djb61 $
 */
public class RescanPostProcessLinksZip implements RescanPostProcessDirInterface {
	private ResourceBundle _bundle;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
	}

	public void postProcessDir(CommandRequest workingDirReq, boolean quiet) {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		// Check if incomplete links are enabled
		if (cfg.getProperty("incomplete.zip.links","false").equals("true")) {
			// check incomplete status and update links
			try {
				ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(workingDirReq.getCurrentDirectory());
				DizStatus dizStatus = zipData.getDizStatus();				
				if (dizStatus.isFinished()) {
					// dir is complete, remove link if needed
					LinkUtils.processLink(workingDirReq, "delete", _bundle);
				}
				else {
					// dir is incomplete, add link if needed
					LinkUtils.processLink(workingDirReq, "create", _bundle);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding zip is unavailable
			} catch (FileNotFoundException e) {
				// No zip in dir
				// We have to make sure there is a .sfv in this dir before deleting
				try {
					ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDirReq.getCurrentDirectory());
					sfvData.getSFVStatus();
				} catch (NoAvailableSlaveException e1) {
					// Slave holding sfv is unavailable
				} catch (FileNotFoundException e1) {
					// No SFV in dir - now we can delete link
					LinkUtils.processLink(workingDirReq, "delete", _bundle);
				} catch (IOException e1) {
					// SFV not readable
				} catch (SlaveUnavailableException e1) {
					// No Slave with SFV available
				}
			} catch (IOException e) {
				// zip not readable
			}
		}
	}

}
