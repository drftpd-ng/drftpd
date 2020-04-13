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
package org.drftpd.zipscript.master.mp3.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.ConfigType;
import org.drftpd.master.commands.list.ListElementsContainer;
import org.drftpd.zipscript.common.mp3.ID3Tag;
import org.drftpd.zipscript.common.mp3.MP3Info;
import org.drftpd.zipscript.master.sfv.list.NoEntryAvailableException;
import org.drftpd.zipscript.master.sfv.list.ZipscriptListStatusBarInterface;
import org.drftpd.zipscript.master.mp3.vfs.ZipscriptVFSDataMP3;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.vfs.DirectoryHandle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptMP3StatusBar implements ZipscriptListStatusBarInterface {

	public ArrayList<String> getStatusBarEntry(DirectoryHandle dir, ListElementsContainer container) throws NoEntryAvailableException {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		// Check config
		Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf", ConfigType.MASTER);
		boolean statusBarEnabled = cfg.getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
		if (statusBarEnabled) {
			try {
				ArrayList<String> statusBarEntries = new ArrayList<>();
				ZipscriptVFSDataMP3 mp3Data = new ZipscriptVFSDataMP3(dir);
				MP3Info mp3Info = mp3Data.getMP3Info();
				Map<String, Object> env = new HashMap<>();
				ID3Tag id3 = mp3Info.getID3Tag();
				if (id3 != null) {
					env.put("artist", id3.getArtist());
					env.put("genre", id3.getGenre());
					env.put("album", id3.getAlbum());
					env.put("year", id3.getYear());
				} else {
					throw new NoEntryAvailableException();
				}
				statusBarEntries.add(container.getSession().jprintf(bundle, "statusbar.id3tag",env,container.getUser()));
				return statusBarEntries;
			} catch (FileNotFoundException e) {
				// Error fetching mp3 info, ignore
			} catch (IOException e) {
				// Error fetching mp3 info, ignore
			} catch (NoAvailableSlaveException e) {
				// Error fetching mp3 info, ignore
			}
		}
		throw new NoEntryAvailableException();
	}
}
