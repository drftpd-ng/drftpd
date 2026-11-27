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
package org.drftpd.zipscript.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;

import java.util.Properties;
import java.util.function.IntSupplier;

/**
 * Thresholds for the halfway and complete race announces, read from zipscript.conf (#582).
 * <p>
 * The defaults reproduce the behaviour from before these settings existed: halfway is announced for
 * releases of 4 or more files, with no size limit, and announces are made whether or not there was a race.
 */
public final class AnnounceThresholds {
    private static final Logger logger = LogManager.getLogger(AnnounceThresholds.class);

    public static final int DEFAULT_HALFWAY_MIN_FILES = 4;

    private final int _halfwayMinFiles;
    private final long _halfwayMinBytes;
    private final boolean _announceNoRace;

    public AnnounceThresholds(int halfwayMinFiles, long halfwayMinBytes, boolean announceNoRace) {
        _halfwayMinFiles = halfwayMinFiles;
        _halfwayMinBytes = halfwayMinBytes;
        _announceNoRace = announceNoRace;
    }

    public static AnnounceThresholds defaults() {
        return new AnnounceThresholds(DEFAULT_HALFWAY_MIN_FILES, 0L, true);
    }

    /** Reads the current zipscript.conf, falling back to the defaults if it cannot be read. */
    public static AnnounceThresholds load() {
        try {
            return fromProperties(ConfigLoader.loadPluginConfig("zipscript.conf"));
        } catch (RuntimeException e) {
            logger.warn("Unable to read zipscript.conf, using default announce thresholds", e);
            return defaults();
        }
    }

    public static AnnounceThresholds fromProperties(Properties props) {
        int minFiles = (int) Math.min(Integer.MAX_VALUE,
                parseLong(props, "halfway.min.files", DEFAULT_HALFWAY_MIN_FILES));
        long minMb = parseLong(props, "halfway.min.size", 0L);
        boolean noRace = !"false".equalsIgnoreCase(props.getProperty("announce.norace", "true").trim());
        return new AnnounceThresholds(minFiles, minMb * 1024L * 1024L, noRace);
    }

    private static long parseLong(Properties props, String key, long def) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            logger.error("Non numeric {} setting in zipscript.conf, using default", key);
            return def;
        }
    }

    /**
     * Number of missing files at or below which a release is past halfway: 50% of the files the
     * SFV (or .diz) lists, rounded down, as it always was.
     */
    public static int halfwayMissing(int totalFiles) {
        return totalFiles / 2;
    }

    /**
     * Whether the halfway announce may be made for this release.
     *
     * @param totalFiles   files listed by the SFV (or .diz)
     * @param releaseBytes bytes uploaded to the release so far
     * @param racers       number of users that uploaded files, only evaluated when needed
     */
    public boolean halfwayAllowed(int totalFiles, long releaseBytes, IntSupplier racers) {
        return totalFiles >= _halfwayMinFiles
                && releaseBytes >= _halfwayMinBytes
                && raceOk(racers);
    }

    /** Whether the complete announce may be made: false for a single uploader when announce.norace=false. */
    public boolean completeAllowed(IntSupplier racers) {
        return raceOk(racers);
    }

    private boolean raceOk(IntSupplier racers) {
        return _announceNoRace || racers.getAsInt() > 1;
    }
}
