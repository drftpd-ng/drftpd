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
package org.drftpd.zipscript.master.sfv;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.GlobPattern;

import java.util.Collection;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Decisions for the optional strictness settings in zipscript.conf (all off by default).
 * Kept free of VFS access so the decisions can be unit tested.
 */
public final class ZipscriptStrictness {

    private static final Logger logger = LogManager.getLogger(ZipscriptStrictness.class);

    // bad globs already reported, so a typo is logged once and not on every upload (bounded)
    private static final Set<String> _warnedGlobs = ConcurrentHashMap.newKeySet();
    private static final int MAX_WARNED = 100;

    private ZipscriptStrictness() {
    }

    private static boolean enabled(Properties cfg, String key) {
        return cfg.getProperty(key, "false").trim().equalsIgnoreCase("true");
    }

    private static boolean isNfo(String name) {
        return name.toLowerCase().endsWith(".nfo");
    }

    /**
     * Decides if an upload of an .nfo has to be denied.
     *
     * @param cfg           zipscript.conf
     * @param uploadName    name of the file being uploaded
     * @param existingNames names of the files already in the directory
     * @return the reason to give to the user, or null if the upload is allowed
     */
    public static String checkNfoUpload(Properties cfg, String uploadName, Collection<String> existingNames) {
        if (!isNfo(uploadName)) {
            return null;
        }
        if (enabled(cfg, "nfo.deny.second")) {
            for (String name : existingNames) {
                if (isNfo(name) && !name.equalsIgnoreCase(uploadName)) {
                    return "Requested action not taken. This release already has an NFO (" + name
                            + "), a second NFO is not allowed.";
                }
            }
        }
        if (enabled(cfg, "nfo.deny.zipdir")) {
            for (String name : existingNames) {
                if (name.toLowerCase().endsWith(".zip")) {
                    return "Requested action not taken. NFO files are not allowed in zip releases ("
                            + name + " is already here).";
                }
            }
        }
        return null;
    }

    /**
     * Decides if the zipscript checks are skipped for a directory ("nocheck.dirs"). Globs are matched
     * case-insensitively, like the nfo.* checks.
     *
     * @param cfg     zipscript.conf
     * @param dirPath path of the directory, with or without a trailing slash
     */
    public static boolean isNoCheckDir(Properties cfg, String dirPath) {
        String globs = cfg.getProperty("nocheck.dirs", "");
        if (globs.isBlank()) {
            return false;
        }
        // Same as path permissions: directories are matched with a trailing slash
        String path = dirPath.endsWith("/") ? dirPath : dirPath + "/";
        StringTokenizer st = new StringTokenizer(globs);
        while (st.hasMoreTokens()) {
            String glob = st.nextToken();
            try {
                if (GlobPattern.compile(glob, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE).matcher(path).matches()) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                // bad glob, ignore it but report it once
                if (_warnedGlobs.size() < MAX_WARNED && _warnedGlobs.add(glob)) {
                    logger.warn("Ignoring invalid glob '{}' in nocheck.dirs: {}", glob, e.getDescription());
                }
            }
        }
        return false;
    }
}
