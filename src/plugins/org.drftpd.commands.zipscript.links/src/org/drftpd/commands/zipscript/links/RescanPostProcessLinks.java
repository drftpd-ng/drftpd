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
package org.drftpd.commands.zipscript.links;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.zipscript.RescanPostProcessDirInterface;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.common.SFVStatus;

/**
 * @author djb61
 * @version $Id$
 */
public class RescanPostProcessLinks implements RescanPostProcessDirInterface {

	private ResourceBundle _bundle;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
	}

	public void postProcessDir(CommandRequest workingDirReq) {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		// Check if incomplete links are enabled
		if (cfg.getProperty("incomplete.links").equals("true")) {
			// check incomplete status and update links
			try {
				ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(workingDirReq.getCurrentDirectory());
				SFVStatus sfvStatus = sfvData.getSFVStatus();
				if (sfvStatus.isFinished()) {
					// dir is complete, remove link if needed
					LinkUtils.processLink(workingDirReq, "delete", _bundle);
				}
				else {
					// dir is incomplete, add link if needed
					LinkUtils.processLink(workingDirReq, "create", _bundle);
				}
			} catch (NoAvailableSlaveException e) {
				// Slave holding sfv is unavailable
			} catch (FileNotFoundException e) {
				// No sfv in dir
			}
		}
	}

}
