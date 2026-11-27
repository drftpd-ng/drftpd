/*
 *  This file is part of DrFTPD, Distributed FTP Daemon.
 *
 *   DrFTPD is free software; you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as
 *   published by
 *   the Free Software Foundation; either version 2 of the
 *   License, or
 *   (at your option) any later version.
 *
 *   DrFTPD is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied
 *   warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *   See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General
 *   Public License
 *   along with DrFTPD; if not, write to the Free
 *   Software
 *   Foundation, Inc., 59 Temple Place, Suite 330,
 *   Boston, MA  02111-1307  USA
 */

package org.drftpd.raceleader.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.ConfigLoader;

import java.util.Properties;

/**
 * Settings for the new race leader announce, read from raceleader.conf (#582).
 * The defaults reproduce the behaviour from before they existed: a 30 minute expiry and an
 * announce for every change of leader.
 */
public final class NewLeaderThresholds {
    private static final Logger logger = LogManager.getLogger(NewLeaderThresholds.class);

    public static final long DEFAULT_EXPIRE_MINUTES = 30;

    private final long _expireMillis;
    private final int _filesAhead;
    private final long _minBytes;

    public NewLeaderThresholds(long expireMinutes, int filesAhead, long minBytes) {
        _expireMillis = expireMinutes * 60_000L;
        _filesAhead = filesAhead;
        _minBytes = minBytes;
    }

    public static NewLeaderThresholds defaults() {
        return new NewLeaderThresholds(DEFAULT_EXPIRE_MINUTES, 0, 0L);
    }

    /** Reads the current raceleader.conf, falling back to the defaults if it cannot be read. */
    public static NewLeaderThresholds load() {
        try {
            return fromProperties(ConfigLoader.loadPluginConfig("raceleader.conf"));
        } catch (RuntimeException e) {
            logger.warn("Unable to read raceleader.conf, using default settings", e);
            return defaults();
        }
    }

    public static NewLeaderThresholds fromProperties(Properties props) {
        long expire = parseLong(props, "newleader.expire", DEFAULT_EXPIRE_MINUTES);
        if (expire < 1) {
            logger.error("newleader.expire in raceleader.conf must be at least 1 minute, using default");
            expire = DEFAULT_EXPIRE_MINUTES;
        }
        int ahead = (int) Math.min(Integer.MAX_VALUE, parseLong(props, "newleader.files.ahead", 0L));
        long minMb = parseLong(props, "newleader.min.size", 0L);
        return new NewLeaderThresholds(expire, ahead, minMb * 1024L * 1024L);
    }

    private static long parseLong(Properties props, String key, long def) {
        String value = props.getProperty(key);
        if (value == null || value.trim().isEmpty()) {
            return def;
        }
        try {
            return Math.max(0L, Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            logger.error("Non numeric {} setting in raceleader.conf, using default", key);
            return def;
        }
    }

    public long getExpireMillis() {
        return _expireMillis;
    }

    /**
     * Whether a user who just took the lead may be announced as the new leader.
     *
     * @param leaderFiles files of the new leader
     * @param leaderBytes bytes of the new leader
     * @param prevFiles   files of the previous leader (0 if they have none left)
     */
    public boolean leaderChangeAllowed(int leaderFiles, long leaderBytes, int prevFiles) {
        if (_filesAhead > 0 && leaderFiles - prevFiles < _filesAhead) {
            return false;
        }
        return leaderBytes >= _minBytes;
    }
}
