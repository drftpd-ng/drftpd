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
package org.drftpd.mediainfo.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.approve.metadata.Approve;
import org.drftpd.master.commands.dataconnection.DataConnectionHandler;
import org.drftpd.master.commands.nuke.NukeEvent;
import org.drftpd.master.commands.nuke.NukeException;
import org.drftpd.master.commands.nuke.NukeUtils;
import org.drftpd.master.commands.nuke.metadata.NukeData;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.mediainfo.common.MediaInfo;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author scitz0
 */
public class MediaInfoPostHook {
    private static final Logger logger = LogManager.getLogger(MediaInfoPostHook.class);

    private final ArrayList<String> _exclSections = new ArrayList<>();
    private final ArrayList<String> _extensions = new ArrayList<>();
    // section name (lowercase) -> allowed audio languages, missing means the check is disabled
    private final Map<String, Set<String>> _allowedLangs = new HashMap<>();
    private final Map<String, Integer> _langNukex = new HashMap<>();
    private final Map<String, Boolean> _langNukeUnknown = new HashMap<>();
    private final ArrayList<Pattern> _langExempt = new ArrayList<>();
    private String _langNuker = "drftpd";

    public MediaInfoPostHook() {
        loadConf();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private void loadConf() {
        Properties cfg = ConfigLoader.loadPluginConfig("mediainfo.conf");
        _exclSections.clear();
        _extensions.clear();
        _allowedLangs.clear();
        _langNukex.clear();
        _langNukeUnknown.clear();
        _langExempt.clear();
        for (String section : cfg.getProperty("sections.exclude", "").split(" ")) {
            section = section.toLowerCase().trim();
            if (!section.isEmpty()) {
                _exclSections.add(section);
            }
        }
        for (String extension : cfg.getProperty("extensions", "").split(" ")) {
            extension = extension.toLowerCase().trim();
            if (!extension.isEmpty()) {
                _extensions.add(extension);
            }
        }
        _langNuker = cfg.getProperty("audio.lang.nuker", "drftpd").trim();
        for (String regex : cfg.getProperty("audio.lang.exempt.dirnames.regex", "").split(";")) {
            if (!regex.trim().isEmpty()) {
                try {
                    _langExempt.add(Pattern.compile(regex.trim()));
                } catch (PatternSyntaxException e) {
                    logger.warn("Invalid audio.lang.exempt.dirnames.regex entry '{}': {}", regex, e.getMessage());
                }
            }
        }
        final String suffix = ".audio.lang.allowed";
        for (String key : cfg.stringPropertyNames()) {
            if (!key.endsWith(suffix)) {
                continue;
            }
            String section = key.substring(0, key.length() - suffix.length()).toLowerCase();
            Set<String> allowed = AudioLanguageRule.parseAllowed(cfg.getProperty(key));
            if (section.isEmpty() || allowed.isEmpty()) {
                continue;
            }
            for (String value : AudioLanguageRule.unmappable(allowed)) {
                logger.warn("{}: allowed audio language '{}' is not a known language name or ISO 639 code, it will only match mediainfo output exactly", key, value);
            }
            _allowedLangs.put(section, allowed);
            int nukex = 3;
            try {
                nukex = Integer.parseInt(cfg.getProperty(key.substring(0, key.length() - "allowed".length()) + "nuke.multiplier", "3").trim());
            } catch (NumberFormatException e) {
                logger.warn("Invalid {}.audio.lang.nuke.multiplier, defaulting to 3", section);
            }
            _langNukex.put(section, nukex);
            _langNukeUnknown.put(section,
                    cfg.getProperty(key.substring(0, key.length() - "allowed".length()) + "unknown", "allow").trim().equalsIgnoreCase("nuke"));
        }
    }

    @CommandHook(commands = "doSTOR", priority = 100, type = HookType.POST)
    public void doSTORPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
            // STOR Failed, skip
            return;
        }

        if (MediaInfoUtils.getValidFileExtension(request.getArgument(), _extensions) == null) {
            return;
        }

        DirectoryHandle workingDir = request.getCurrentDirectory();
        FileHandle file;
        try {
            file = response.getObject(DataConnectionHandler.TRANSFER_FILE);
        } catch (KeyNotFoundException e) {
            // Couldn't find the uploaded file
            return;
        }

        SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(workingDir);
        if (_exclSections.contains(sec.getName().toLowerCase())) {
            return;
        }

        MediaInfo mediaInfo;
        MediaInfoVFSData mediaData = new MediaInfoVFSData(file);
        try {
            mediaInfo = mediaData.getMediaInfo();
        } catch (IOException | NoAvailableSlaveException | SlaveUnavailableException e) {
            // Routine when a slave is offline, so the stack trace is only logged at debug level
            logger.warn("MediaInfo lookup failed: {}", e.getMessage());
            logger.debug("MediaInfo lookup failed", e);
            return;
        }

        GlobalContext.getEventService().publishAsync(new MediaInfoEvent(mediaInfo, workingDir));

        checkAudioLanguage(mediaInfo, workingDir, sec);
    }

    /**
     * Nukes the release if none of the sample's audio streams has an allowed language.
     * Disabled unless {@code <section>.audio.lang.allowed} is set for the section.
     */
    private void checkAudioLanguage(MediaInfo mediaInfo, DirectoryHandle dir, SectionInterface sec) {
        String secName = sec.getName().toLowerCase();
        Set<String> allowed = _allowedLangs.get(secName);
        if (allowed == null || !mediaInfo.getSampleOk()) {
            // Not configured, or sample unreadable/incomplete so the audio info can't be trusted
            return;
        }
        List<String> languages = new ArrayList<>();
        for (HashMap<String, String> audio : mediaInfo.getAudioInfos()) {
            languages.add(audio.get("Language"));
        }
        if (!AudioLanguageRule.shouldNuke(languages, allowed, _langNukeUnknown.get(secName))) {
            return;
        }

        // The sample normally lives in a Sample subdir, the release is its parent
        DirectoryHandle release = dir;
        if (release.getName().equalsIgnoreCase("sample") && !release.isRoot()) {
            release = release.getParent();
        }
        for (Pattern exempt : _langExempt) {
            if (exempt.matcher(release.getName()).matches()) {
                return;
            }
        }
        if (release.isRoot() || release.getName().startsWith("[NUKED]-") || Approve.isApproved(release)) {
            return;
        }

        User nuker;
        try {
            nuker = GlobalContext.getGlobalContext().getUserManager().getUserByNameUnchecked(_langNuker);
        } catch (NoSuchUserException | UserFileException e) {
            logger.error("Error loading audio language nuker '{}': {}", _langNuker, e.getMessage());
            return;
        }
        String reason = "Audio language not allowed (found: " + languages + ", allowed: " + allowed + ")";
        try {
            NukeData nd = NukeUtils.nuke(release, _langNukex.get(secName), reason, nuker);
            GlobalContext.getEventService().publishAsync(new NukeEvent(nuker, "NUKE", nd));
        } catch (NukeException e) {
            logger.error("Error nuking {} for audio language: {}", release.getPath(), e.getMessage());
        }
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        loadConf();
    }

}
