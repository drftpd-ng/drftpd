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
package org.drftpd.plugins.newraceleader;

import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.RankUtils;
import org.drftpd.master.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.util.UploaderPosition;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeaderHooks.java 2393 2011-04-11 20:47:51Z cyber1331 $
 */
public class NewRaceLeaderHooks  {

	private NewRaceLeaderManager _newraceleadermanager;

	public NewRaceLeaderHooks() {
		_newraceleadermanager = NewRaceLeaderManager.getNewRaceLeaderManager();
	}

	@CommandHook(commands = "doSTOR", priority = 12, type = HookType.POST)
	public void doSTORPostHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// Transfer failed, abort checks
			return;
		}

		FileHandle transferFile;
		try {
			transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}

		if (transferFile.getName().contains(".*\\.(sfv|nfo|diz)$")) {
			// no need to check as these files do not matter
			return;
		}

		ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(transferFile.getParent());
		try {
			SFVInfo sfvinfo = sfvData.getSFVInfo();
			// Make sure release is > 5 files (No point in spaming a small release
			if (sfvinfo.getSize() > 5) {
				SFVStatus sfvstatus = sfvData.getSFVStatus();
				Collection<UploaderPosition> racers = RankUtils.userSort(SFVTools.getSFVFiles(transferFile.getParent(), sfvData),"bytes", "high");

				// Check if file uploaded is in SFV
				if (sfvinfo.getEntries().get(transferFile.getName()) == null) {
					return;
				}

				// Check if release is finished
				if (sfvstatus.isFinished()) {
					_newraceleadermanager.delete(transferFile.getParent());
				} else {
					_newraceleadermanager.check(transferFile,sfvstatus.getMissing(),sfvinfo.getSize(),racers);
				}
			}
		} catch (FileNotFoundException ex) {
			//no SFV file - Ignore
		} catch (IOException ex) {
			//cannot't read sfv file - Ignore
		} catch (NoAvailableSlaveException | SlaveUnavailableException e) {
			//No slaves with SFV - Ignore
		}
	}
}
