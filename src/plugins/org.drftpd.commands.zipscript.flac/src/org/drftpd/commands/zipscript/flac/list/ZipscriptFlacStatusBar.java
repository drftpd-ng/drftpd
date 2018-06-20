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
package org.drftpd.commands.zipscript.flac.list;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import org.drftpd.GlobalContext;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.zipscript.list.NoEntryAvailableException;
import org.drftpd.commands.zipscript.list.ZipscriptListStatusBarInterface;
import org.drftpd.commands.zipscript.flac.vfs.ZipscriptVFSDataFlac;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;
import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author norox
 */
public class ZipscriptFlacStatusBar implements ZipscriptListStatusBarInterface {

	public ArrayList<String> getStatusBarEntry(DirectoryHandle dir, ListElementsContainer container) throws NoEntryAvailableException {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";
		// Check config
		boolean statusBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
		if (statusBarEnabled) {
			try {
				ArrayList<String> statusBarEntries = new ArrayList<>();
				ZipscriptVFSDataFlac flacData = new ZipscriptVFSDataFlac(dir);
				FlacInfo flacInfo = flacData.getFlacInfo();
				ReplacerEnvironment env = new ReplacerEnvironment();
				VorbisTag vorbistag = flacInfo.getVorbisTag();
				if (vorbistag != null) {
					env.add("artist", vorbistag.getArtist());
					env.add("genre", vorbistag.getGenre());
					env.add("album", vorbistag.getAlbum());
					env.add("year", vorbistag.getYear());
				} else {
					throw new NoEntryAvailableException();
				}
				statusBarEntries.add(container.getSession().jprintf(bundle,
						keyPrefix + "statusbar.vorbistag", env, container.getUser()));
				return statusBarEntries;
			} catch (FileNotFoundException e) {
				// Error fetching flac info, ignore
			} catch (IOException e) {
				// Error fetching flac info, ignore
			} catch (NoAvailableSlaveException e) {
				// Error fetching flac info, ignore
			}
		}
		throw new NoEntryAvailableException();
	}
}
