/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.zipscript.master.mp3.hooks;

import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandManagerInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.dataconnection.DataConnectionHandler;
import org.drftpd.master.commands.dir.Dir;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.zipscript.common.mp3.ID3Tag;
import org.drftpd.zipscript.common.mp3.MP3Info;
import org.drftpd.zipscript.master.mp3.event.MP3Event;
import org.drftpd.zipscript.master.mp3.vfs.ZipscriptVFSDataMP3;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptMP3PostHook {

    private final ResourceBundle _bundle;


    public ZipscriptMP3PostHook(CommandManagerInterface manager) {
        _bundle = manager.getResourceBundle();
    }

    @CommandHook(commands = "doCWD", priority = 12, type = HookType.POST)
    public void doZipscriptCWDMP3Hook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // CWD failed, abort info
            return;
        }
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        if (cfg.getProperty("cwd.mp3info.enabled", "false").equalsIgnoreCase("true")) {
            addMP3Info(request, response, response.getCurrentDirectory(), false);
        }
    }

    @CommandHook(commands = "doSTOR", priority = 12, type = HookType.POST)
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

    @CommandHook(commands = "doDELE", priority = 12, type = HookType.POST)
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
                for (FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
                    if (file.getName().toLowerCase().endsWith(".mp3")) {
                        noMP3 = false;
                    }
                }
                if (noMP3) {
                    request.getCurrentDirectory().removePluginMetaData(MP3Info.MP3INFO);
                }
            } catch (FileNotFoundException e) {
                // No inode to remove mp3info from or dir has been deleted
            }
        }
    }

    private void addMP3Info(CommandRequest request, CommandResponse response, InodeHandle inode, boolean isStor) {
        // show race stats
        try {
            ZipscriptVFSDataMP3 mp3Data = new ZipscriptVFSDataMP3(inode);
            MP3Info mp3Info = mp3Data.getMP3Info();

            Map<String, Object> env = request.getSession().getReplacerEnvironment(null,
                    request.getSession().getUserNull(request.getUser()));
            ID3Tag id3 = mp3Info.getID3Tag();
            if (id3 != null) {
                env.put("artist", id3.getArtist());
                env.put("genre", id3.getGenre());
                env.put("album", id3.getAlbum());
                env.put("year", id3.getYear());
                env.put("title", id3.getTitle());
                if (id3.getTrack() == 0) {
                    env.put("track", "");
                } else {
                    env.put("track", id3.getTrack());
                }
            } else {
                env.put("artist", "unknown");
                env.put("genre", "unknown");
                env.put("album", "unknown");
                env.put("year", "unknown");
                env.put("title", "unknown");
                env.put("track", "unknown");
            }
            env.put("bitrate", mp3Info.getBitrate() / 1000 + " kbit/s " + mp3Info.getEncodingtype());
            env.put("samplerate", mp3Info.getSamplerate());
            env.put("stereomode", mp3Info.getStereoMode());
            int runSeconds = (int) (mp3Info.getRuntime() / 1000);
            String runtime = "";
            if (runSeconds > 59) {
                int runMins = runSeconds / 60;
                runSeconds %= 60;
                runtime = runMins + "m ";
            }
            runtime = runtime + runSeconds + "s";
            env.put("runtime", runtime);

            if (isStor) {
                Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
                if (cfg.getProperty("stor.mp3info.enabled", "false").equalsIgnoreCase("true")) {
                    response.addComment(request.getSession().jprintf(_bundle, env, "stor.mp3info.text"));
                }
            } else {
                response.addComment(request.getSession().jprintf(_bundle, env, "cwd.mp3info.text"));
            }
        } catch (IOException | NoAvailableSlaveException e) {
            // Error fetching mp3 info, ignore
        }
    }
}
