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
package org.drftpd.zipscript.master.announce;

import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.common.mp3.MP3Info;
import org.drftpd.zipscript.master.mp3.vfs.ZipscriptVFSDataMP3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/**
 * Decides which release type a transferred file belongs to, so announcers can look up
 * per-type theme keys (e.g. <code>sfv.store.complete.mp3.vbr</code>) before the generic one.
 * <p>
 * The returned list is ordered from most to least specific. Known types are
 * <code>mp3.cbr</code>, <code>mp3.vbr</code>, <code>mp3</code>, <code>flac</code>,
 * <code>audio</code>, <code>rar</code>, <code>video</code> and <code>zip</code>.
 */
public final class ReleaseTypes {

    private static final Set<String> VIDEO_EXTS = Set.of("avi", "mkv", "mp4", "m4v", "mpg", "mpeg",
            "wmv", "mov", "vob", "m2ts", "ts", "iso");
    private static final Set<String> AUDIO_EXTS = Set.of("m4a", "ogg", "opus", "wav", "aac", "wma", "ape");
    private static final Pattern RAR_VOLUME = Pattern.compile("r\\d{2,3}");

    private ReleaseTypes() {
    }

    /**
     * @param fileName     name of the file just transferred
     * @param encodingType supplies the MP3 encoding type ("CBR" or "VBR") and is only called for
     *                     mp3 files; may return null or throw, in which case the generic mp3 type is used
     * @return types from most to least specific, empty when the type is unknown
     */
    public static List<String> detect(String fileName, Supplier<String> encodingType) {
        if (fileName == null) {
            return Collections.emptyList();
        }
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return Collections.emptyList();
        }
        String ext = fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
        List<String> types = new ArrayList<>(3);
        if (ext.equals("mp3")) {
            String enc = null;
            try {
                enc = encodingType == null ? null : encodingType.get();
            } catch (RuntimeException e) {
                // fall back to the plain mp3 type
            }
            if (enc != null) {
                String lower = enc.trim().toLowerCase(Locale.ROOT);
                if (lower.equals("cbr") || lower.equals("vbr")) {
                    types.add("mp3." + lower);
                }
            }
            types.add("mp3");
            types.add("audio");
        } else if (ext.equals("flac")) {
            types.add("flac");
            types.add("audio");
        } else if (AUDIO_EXTS.contains(ext)) {
            types.add("audio");
        } else if (ext.equals("rar") || RAR_VOLUME.matcher(ext).matches()) {
            types.add("rar");
        } else if (VIDEO_EXTS.contains(ext)) {
            types.add("video");
        } else if (ext.equals("zip")) {
            types.add("zip");
        }
        return types;
    }

    /** Per-type themes are opt-in: <code>irc.themes.bytype=true</code> in zipscript.conf. */
    public static boolean isEnabled(Properties cfg) {
        return Boolean.parseBoolean(cfg.getProperty("irc.themes.bytype", "false").trim());
    }

    /**
     * Sets the type chain for the calling thread, if per-type themes are enabled. The caller must
     * call {@link TypeAwareBundle#clearTypes()} when done. The MP3 encoding is only looked up
     * (which can ask a slave) when the theme actually defines .mp3.cbr/.mp3.vbr keys.
     */
    public static void apply(ResourceBundle bundle, DirectoryHandle dir, String fileName) {
        TypeAwareBundle.clearTypes();
        if (!(bundle instanceof TypeAwareBundle) || !isEnabled(ConfigLoader.loadPluginConfig("zipscript.conf"))) {
            return;
        }
        boolean wantEncoding = ((TypeAwareBundle) bundle).hasKeySuffix(".mp3.cbr", ".mp3.vbr");
        TypeAwareBundle.setTypes(detect(fileName, wantEncoding ? () -> {
            try {
                MP3Info info = new ZipscriptVFSDataMP3(dir).getMP3Info();
                return info == null ? null : info.getEncodingtype();
            } catch (Exception e) {
                return null;
            }
        } : null));
    }
}
