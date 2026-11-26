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
package org.drftpd.zipscript.master.flac.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
import org.drftpd.zipscript.common.flac.FlacInfo;
import org.drftpd.zipscript.common.flac.VorbisTag;
import org.drftpd.zipscript.master.audio.AudioRules;
import org.drftpd.zipscript.master.audio.AudioRulesEnforcer;
import org.drftpd.zipscript.master.flac.vfs.ZipscriptVFSDataFlac;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;

/**
 * @author norox
 */
public class ZipscriptFlacPostHook {
    private static final Logger logger = LogManager.getLogger(ZipscriptFlacPostHook.class);


    private final ResourceBundle _bundle;

    public ZipscriptFlacPostHook(CommandManagerInterface manager) {
        _bundle = manager.getResourceBundle();
    }

    @CommandHook(commands = "doCWD", priority = 12, type = HookType.POST)
    public void doZipscriptCWDFlacHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // CWD failed, abort info
            return;
        }
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
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
                if (rejectedByAudioRules(response, transferFile)) {
                    return;
                }
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
                for (FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
                    if (file.getName().toLowerCase().endsWith(".flac")) {
                        noFlac = false;
                    }
                }
                if (noFlac) {
                    request.getCurrentDirectory().removePluginMetaData(FlacInfo.FLACINFO);
                }
            } catch (FileNotFoundException e) {
                // No inode to remove flacinfo from or dir has been deleted
            }
        }
    }

    /**
     * Applies the per section audio rules (genre and year only, opt-in, see zipscript.conf).
     *
     * @return true if the release was removed
     */
    private boolean rejectedByAudioRules(CommandResponse response, FileHandle transferFile) {
        AudioRules rules = AudioRulesEnforcer.rulesFor(transferFile, ".flac");
        if (rules == null) {
            return false;
        }
        try {
            FlacInfo info = new ZipscriptVFSDataFlac(transferFile).getFlacInfo();
            VorbisTag tag = info.getVorbisTag();
            return AudioRulesEnforcer.enforce(response, transferFile, rules,
                    tag == null ? null : tag.getGenre(), tag == null ? null : tag.getYear(), 0, false);
        } catch (IOException | NoAvailableSlaveException e) {
            logger.debug("Unable to check audio rules for {}: {}", transferFile.getPath(), e.getMessage());
            return false;
        }
    }

    private void addFlacInfo(CommandRequest request, CommandResponse response, InodeHandle inode, boolean isStor) {
        // show race stats
        try {
            ZipscriptVFSDataFlac flacData = new ZipscriptVFSDataFlac(inode);
            FlacInfo flacInfo = flacData.getFlacInfo();

            Map<String, Object> env = request.getSession().getReplacerEnvironment(null,
                    request.getSession().getUserNull(request.getUser()));
            VorbisTag vorbistag = flacInfo.getVorbisTag();
            if (vorbistag != null) {
                env.put("artist", vorbistag.getArtist());
                env.put("genre", vorbistag.getGenre());
                env.put("album", vorbistag.getAlbum());
                env.put("year", vorbistag.getYear());
                env.put("title", vorbistag.getTitle());
                if (vorbistag.getTrack() == 0) {
                    env.put("track", "");
                } else {
                    env.put("track", vorbistag.getTrack());
                }
            } else {
                env.put("artist", "unknown");
                env.put("genre", "unknown");
                env.put("album", "unknown");
                env.put("year", "unknown");
                env.put("title", "unknown");
                env.put("track", "unknown");
            }
            env.put("samplerate", flacInfo.getSamplerate());
            env.put("channels", flacInfo.getChannels());
            int runSeconds = (int) flacInfo.getRuntime();
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
                if (cfg.getProperty("stor.flacinfo.enabled", "false").equalsIgnoreCase("true")) {
                    response.addComment(request.getSession().jprintf(_bundle, env, "stor.flacinfo.text"));
                }
            } else {
                response.addComment(request.getSession().jprintf(_bundle, env, "cwd.flacinfo.text"));
            }
        } catch (NoAvailableSlaveException | IOException e) {
            // Error fetching flac info, ignore
            logger.debug("Unable to show FLAC info for {}: {}", inode.getPath(), e.getMessage());
        }
    }
}
