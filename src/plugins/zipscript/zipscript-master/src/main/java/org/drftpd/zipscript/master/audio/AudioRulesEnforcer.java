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
package org.drftpd.zipscript.master.audio;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;

import java.io.FileNotFoundException;
import java.util.List;
import java.util.Properties;

/**
 * Applies the per section {@link AudioRules} to an uploaded audio file.
 */
public final class AudioRulesEnforcer {
    private static final Logger logger = LogManager.getLogger(AudioRulesEnforcer.class);

    private AudioRulesEnforcer() {
    }

    /**
     * Loads the audio rules that apply to an uploaded audio file. Returns null (nothing to check) if
     * the rules of its section are disabled, or if the file is not the only one of its type in its
     * directory.
     * <p>
     * Only the first audio file of a release is checked: the count is taken when the upload
     * finishes, so two files of the same release finishing at the same moment both see a count of
     * 2 and neither is checked, and a bad file uploaded after the first one is not checked either.
     *
     * @param extension extension (with dot, lowercase) of the audio files, e.g. ".mp3"
     */
    public static AudioRules rulesFor(FileHandle file, String extension) {
        try {
            DirectoryHandle dir = file.getParent();
            SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
            Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
            AudioRules rules = AudioRules.load(cfg, section.getName());
            if (rules == null) {
                return null;
            }
            int count = 0;
            for (FileHandle f : dir.getFilesUnchecked()) {
                if (f.getName().toLowerCase().endsWith(extension)) {
                    count++;
                }
            }
            return count == 1 ? rules : null;
        } catch (FileNotFoundException e) {
            logger.debug("Audio rules: {} is gone, skipping", file.getPath());
            return null;
        }
    }

    /**
     * Applies the rules returned by {@link #rulesFor} to the tag values of the file. A rejected
     * release is removed as a whole, or only warned about.
     *
     * @return true if the release was deleted
     */
    public static boolean enforce(CommandResponse response, FileHandle file, AudioRules rules,
                                  String genre, String year, int bitrateKbps, boolean vbr) {
        try {
            DirectoryHandle dir = file.getParent();
            SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
            List<String> reasons = rules.evaluate(genre, year, bitrateKbps, vbr);
            if (reasons.isEmpty()) {
                return false;
            }
            String reason = String.join(", ", reasons);
            String releasePath = AudioRules.releasePath(section.getCurrentDirectory().getPath(), file.getPath());
            boolean delete = rules.isDelete() && releasePath != null;
            logger.info("Audio rules of section {} violated by {}: {} ({})", section.getName(), file.getPath(),
                    reason, delete ? "deleting release " + releasePath : "warning only");
            if (delete) {
                response.addComment("Audio rules: " + reason + ". Release rejected and removed.");
                try {
                    new DirectoryHandle(releasePath).deleteUnchecked();
                } catch (RuntimeException e) {
                    logger.warn("Audio rules: unable to delete release {}", releasePath, e);
                    return false;
                }
                return true;
            }
            response.addComment("Audio rules: " + reason
                    + (rules.isDelete() ? " (release not removed: not inside a release directory)." : " (warning only)."));
        } catch (FileNotFoundException e) {
            logger.debug("Audio rules: {} is gone, skipping", file.getPath());
        }
        return false;
    }
}
