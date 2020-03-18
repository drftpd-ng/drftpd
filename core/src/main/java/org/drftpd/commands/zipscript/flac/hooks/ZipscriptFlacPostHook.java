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
package org.drftpd.commands.zipscript.flac.hooks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.flac.event.FlacEvent;
import org.drftpd.commands.zipscript.flac.vfs.ZipscriptVFSDataFlac;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.master.ConnectionManager;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.plugins.commandmanager.CommandRequest;
import org.drftpd.plugins.commandmanager.CommandResponse;
import org.drftpd.plugins.commandmanager.StandardCommandManager;
import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author norox
 */
public class ZipscriptFlacPostHook {

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public ZipscriptFlacPostHook() {
		_bundle = ConnectionManager.getConnectionManager().getCommandManager().getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	@CommandHook(commands = "doCWD", priority = 12, type = HookType.POST)
	public void doZipscriptCWDFlacHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// CWD failed, abort info
			return;
		}
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("cwd.flacinfo.enabled", "false").equalsIgnoreCase("true")) {
			addFlacInfo(request, response, response.getCurrentDirectory(), false);
		}
	}

	@CommandHook(commands = "doSTOR", priority = 12, type = HookType.POST)
	public void doZipscriptSTORFlacHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort info
			return;
		}
		FileHandle transferFile;
		try {
			transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
			if (transferFile.getName().toLowerCase().endsWith(".flac")) {
				addFlacInfo(request, response, transferFile, true);
			}
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
        }
	}

	@CommandHook(commands = "doDELE", priority = 12, type = HookType.POST)
	public void doZipscriptDELEFlacHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// DELE failed, abort info
			return;
		}
		String deleFileName;
		try {
			deleFileName = response.getObject(Dir.FILENAME);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}
		if (deleFileName.toLowerCase().endsWith(".flac")) {
			try {
				boolean noFlac = true;
				// Check if there are any other flac's left
				for(FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
					if (file.getName().toLowerCase().endsWith(".flac")) {
						noFlac = false;
					}
				}
				if (noFlac) {
					request.getCurrentDirectory().removePluginMetaData(FlacInfo.FLACINFO);
				}
			} catch(FileNotFoundException e) {
				// No inode to remove flacinfo from or dir has been deleted
			}
		}
	}

	private void addFlacInfo(CommandRequest request, CommandResponse response, InodeHandle inode, boolean isStor) {
		// show race stats
		try {
			ZipscriptVFSDataFlac flacData = new ZipscriptVFSDataFlac(inode);
			FlacInfo flacInfo = flacData.getFlacInfo();

			ReplacerEnvironment env = request.getSession().getReplacerEnvironment(null,
					request.getSession().getUserNull(request.getUser()));
			VorbisTag vorbistag = flacInfo.getVorbisTag();
			if (vorbistag != null) {
				env.add("artist", vorbistag.getArtist());
				env.add("genre", vorbistag.getGenre());
				env.add("album", vorbistag.getAlbum());
				env.add("year", vorbistag.getYear());
				env.add("title", vorbistag.getTitle());
				if (vorbistag.getTrack() == 0) {
					env.add("track","");
				} else {
					env.add("track", vorbistag.getTrack());
				}
			} else {
				env.add("artist", "unknown");
				env.add("genre", "unknown");
				env.add("album", "unknown");
				env.add("year", "unknown");
				env.add("title", "unknown");
				env.add("track", "unknown");
			}
			env.add("samplerate", flacInfo.getSamplerate());
			env.add("channels", flacInfo.getChannels());
			int runSeconds = (int)flacInfo.getRuntime();
			String runtime = "";
			if (runSeconds > 59) {
				int runMins = runSeconds / 60;
				runSeconds %= 60;
				runtime = runMins + "m ";
			}
			runtime = runtime + runSeconds + "s";
			env.add("runtime", runtime);

			if (isStor) {
				Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("zipscript.conf");
				if (cfg.getProperty("stor.flacinfo.enabled", "false").equalsIgnoreCase("true")) {
					response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "stor.flacinfo.text"));
				}
				FileHandle file = (FileHandle) inode;
				GlobalContext.getEventService().publishAsync(new FlacEvent(flacInfo, file.getParent(), flacData.isFirst()));
			} else {
				response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix + "cwd.flacinfo.text"));
			}
		} catch (FileNotFoundException e) {
			// Error fetching flac info, ignore
		} catch (IOException e) {
			// Error fetching flac info, ignore
		} catch (NoAvailableSlaveException e) {
			// Error fetching flac info, ignore
		}
	}
}
