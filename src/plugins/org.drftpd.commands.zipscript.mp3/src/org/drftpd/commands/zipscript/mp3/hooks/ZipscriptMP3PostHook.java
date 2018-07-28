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
package org.drftpd.commands.zipscript.mp3.hooks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.mp3.event.MP3Event;
import org.drftpd.commands.zipscript.mp3.vfs.ZipscriptVFSDataMP3;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.mp3.common.ID3Tag;
import org.drftpd.protocol.zipscript.mp3.common.MP3Info;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptMP3PostHook implements PostHookInterface {

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public void doZipscriptCWDMP3Hook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// CWD failed, abort info
			return;
		}
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("cwd.mp3info.enabled", "false").equalsIgnoreCase("true")) {
			addMP3Info(request, response, response.getCurrentDirectory(), false);
		}
	}

	public void doZipscriptSTORMP3Hook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort info
			return;
		}
		FileHandle transferFile;
		try {
			transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
			if (transferFile.getName().toLowerCase().endsWith(".mp3")) {
				addMP3Info(request, response, transferFile, true);
			}
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
        }
	}

	public void doZipscriptDELEMP3Hook(CommandRequest request, CommandResponse response) {
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
		if (deleFileName.toLowerCase().endsWith(".mp3")) {
			try {
				boolean noMP3 = true;
				// Check if there are any other mp3s left
				for(FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
					if (file.getName().toLowerCase().endsWith(".mp3")) {
						noMP3 = false;
					}
				}
				if (noMP3) {
					request.getCurrentDirectory().removePluginMetaData(MP3Info.MP3INFO);
				}
			} catch(FileNotFoundException e) {
				// No inode to remove mp3info from or dir has been deleted
			}
		}
	}

	private void addMP3Info(CommandRequest request, CommandResponse response, InodeHandle inode, boolean isStor) {
		// show race stats
		try {
			ZipscriptVFSDataMP3 mp3Data = new ZipscriptVFSDataMP3(inode);
			MP3Info mp3Info = mp3Data.getMP3Info();

			ReplacerEnvironment env = request.getSession().getReplacerEnvironment(null,
					request.getSession().getUserNull(request.getUser()));
			ID3Tag id3 = mp3Info.getID3Tag();
			if (id3 != null) {
				env.add("artist", id3.getArtist());
				env.add("genre", id3.getGenre());
				env.add("album", id3.getAlbum());
				env.add("year", id3.getYear());
				env.add("title", id3.getTitle());
				if (id3.getTrack() == 0) {
					env.add("track","");
				} else {
					env.add("track", id3.getTrack());
				}
			} else {
				env.add("artist", "unknown");
				env.add("genre", "unknown");
				env.add("album", "unknown");
				env.add("year", "unknown");
				env.add("title", "unknown");
				env.add("track", "unknown");
			}
			env.add("bitrate", Integer.toString(mp3Info.getBitrate() / 1000) + " kbit/s " + mp3Info.getEncodingtype());
			env.add("samplerate", mp3Info.getSamplerate());
			env.add("stereomode", mp3Info.getStereoMode());
			int runSeconds = (int) (mp3Info.getRuntime() / 1000);
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
				if (cfg.getProperty("stor.mp3info.enabled", "false").equalsIgnoreCase("true")) {
					response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix+"stor.mp3info.text"));
				}
				FileHandle file = (FileHandle) inode;
				GlobalContext.getEventService().publishAsync(new MP3Event(mp3Info,file.getParent(),mp3Data.isFirst()));
			} else {
				response.addComment(request.getSession().jprintf(_bundle, env, _keyPrefix+"cwd.mp3info.text"));
			}
		} catch (FileNotFoundException e) {
			// Error fetching mp3 info, ignore
		} catch (IOException e) {
			// Error fetching mp3 info, ignore
		} catch (NoAvailableSlaveException e) {
			// Error fetching mp3 info, ignore
		}
	}
}
