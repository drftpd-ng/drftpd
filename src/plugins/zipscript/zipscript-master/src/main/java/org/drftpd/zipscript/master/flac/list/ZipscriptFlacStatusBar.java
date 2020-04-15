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
package org.drftpd.zipscript.master.flac.list;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.commands.list.ListElementsContainer;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.common.flac.FlacInfo;
import org.drftpd.zipscript.common.flac.VorbisTag;
import org.drftpd.zipscript.master.flac.vfs.ZipscriptVFSDataFlac;
import org.drftpd.zipscript.master.sfv.list.NoEntryAvailableException;
import org.drftpd.zipscript.master.sfv.list.ZipscriptListStatusBarInterface;

import java.io.IOException;
import java.util.*;

/**
 * @author norox
 */
public class ZipscriptFlacStatusBar implements ZipscriptListStatusBarInterface {

    public ArrayList<String> getStatusBarEntry(DirectoryHandle dir, ListElementsContainer container) throws NoEntryAvailableException {
        ResourceBundle bundle = container.getCommandManager().getResourceBundle();
        // Check config
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        boolean statusBarEnabled = cfg.getProperty("statusbar.enabled", "false").equalsIgnoreCase("true");
        if (statusBarEnabled) {
            try {
                ArrayList<String> statusBarEntries = new ArrayList<>();
                ZipscriptVFSDataFlac flacData = new ZipscriptVFSDataFlac(dir);
                FlacInfo flacInfo = flacData.getFlacInfo();
                Map<String, Object> env = new HashMap<>();
                VorbisTag vorbistag = flacInfo.getVorbisTag();
                if (vorbistag != null) {
                    env.put("artist", vorbistag.getArtist());
                    env.put("genre", vorbistag.getGenre());
                    env.put("album", vorbistag.getAlbum());
                    env.put("year", vorbistag.getYear());
                } else {
                    throw new NoEntryAvailableException();
                }
                statusBarEntries.add(container.getSession().jprintf(bundle, "statusbar.vorbistag", env, container.getUser()));
                return statusBarEntries;
            } catch (IOException | NoAvailableSlaveException e) {
                // Error fetching flac info, ignore
            }
        }
        throw new NoEntryAvailableException();
    }
}
