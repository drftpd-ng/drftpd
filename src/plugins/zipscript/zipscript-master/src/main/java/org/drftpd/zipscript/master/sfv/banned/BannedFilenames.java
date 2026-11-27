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
package org.drftpd.zipscript.master.sfv.banned;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.GlobPattern;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Matches file names against a configured list of glob patterns (opt-in, see
 * {@code banned.filenames} in zipscript.conf).
 * <p>
 * Patterns use the same glob syntax as perms.conf ({@code *}, {@code ?}, {@code [...]}), are matched
 * case-insensitively against the complete file name (not the path), and, unlike a shell, {@code *} also
 * matches a leading dot. Optional per-directory overrides replace the global list for uploads whose
 * directory matches the override path glob.
 */
public final class BannedFilenames {

    private static final Logger logger = LogManager.getLogger(BannedFilenames.class);

    private static final int FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE;

    private final List<Pattern> _global;
    private final List<DirOverride> _overrides;

    private record DirOverride(Pattern path, List<Pattern> patterns) {
    }

    private BannedFilenames(List<Pattern> global, List<DirOverride> overrides) {
        _global = global;
        _overrides = overrides;
    }

    /**
     * Parse a comma separated list of globs. Blank entries are ignored and invalid ones are skipped (with a warning naming the glob).
     */
    static List<Pattern> parsePatterns(String list) {
        List<Pattern> result = new ArrayList<>();
        if (list == null) {
            return result;
        }
        for (String token : list.split(",")) {
            String glob = token.trim();
            if (glob.isEmpty()) {
                continue;
            }
            try {
                result.add(GlobPattern.compile(glob, FLAGS));
            } catch (PatternSyntaxException e) {
                // ignore it rather than breaking uploads, but make the typo visible
                logger.warn("Ignoring invalid glob '{}' in banned.filenames: {}", glob, e.getDescription());
            }
        }
        return result;
    }

    /**
     * Build from zipscript.conf properties. Keys: {@code banned.filenames} (global list) and
     * {@code banned.filenames.override.N.path} / {@code banned.filenames.override.N.patterns}, N = 1, 2, ...
     * (numbering stops at the first N without a path).
     */
    public static BannedFilenames fromProperties(Properties cfg) {
        List<Pattern> global = parsePatterns(cfg.getProperty("banned.filenames", ""));
        List<DirOverride> overrides = new ArrayList<>();
        for (int i = 1; ; i++) {
            String path = cfg.getProperty("banned.filenames.override." + i + ".path");
            if (path == null || path.isBlank()) {
                break;
            }
            try {
                overrides.add(new DirOverride(GlobPattern.compile(path.trim(), FLAGS),
                        parsePatterns(cfg.getProperty("banned.filenames.override." + i + ".patterns", ""))));
            } catch (PatternSyntaxException e) {
                logger.warn("Ignoring banned.filenames.override.{} with invalid path glob '{}': {}",
                        i, path.trim(), e.getDescription());
            }
        }
        return new BannedFilenames(global, overrides);
    }

    /**
     * @return true when no pattern is configured at all (feature off)
     */
    public boolean isEmpty() {
        return _global.isEmpty() && _overrides.isEmpty();
    }

    /**
     * @param dirPath  path of the directory the file is uploaded to, e.g. /MP3/Release
     * @param fileName name of the file, without path
     * @return true if the upload must be refused
     */
    public boolean isBanned(String dirPath, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            return false;
        }
        List<Pattern> active = _global;
        for (DirOverride o : _overrides) {
            if (o.path().matcher(dirPath).matches()) {
                active = o.patterns();
                break;
            }
        }
        for (Pattern p : active) {
            if (p.matcher(fileName).matches()) {
                return true;
            }
        }
        return false;
    }
}
