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

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Decides when an UPDATE announce is due for a long race: after every N files and/or every N
 * percent, but at most once per minimum interval per release directory. Disabled unless at least
 * one of <code>irc.update.files</code> / <code>irc.update.percent</code> is set above 0.
 * <p>
 * Settings (zipscript.conf): <code>irc.update.files</code>, <code>irc.update.percent</code>,
 * <code>irc.update.interval</code> (seconds, default 60).
 */
public class UpdateThrottle {

    private static final int MAX_TRACKED = 500;

    /** Parsed settings; a value of 0 or less for files/percent means that trigger is off. */
    public static final class Settings {
        final int _files;
        final int _percent;
        final long _intervalMillis;

        public Settings(int files, int percent, long intervalSeconds) {
            _files = Math.max(0, files);
            _percent = Math.max(0, Math.min(100, percent));
            _intervalMillis = Math.max(0, intervalSeconds) * 1000L;
        }

        public static Settings fromProperties(Properties cfg) {
            return new Settings(parse(cfg, "irc.update.files", 0), parse(cfg, "irc.update.percent", 0),
                    parse(cfg, "irc.update.interval", 60));
        }

        private static int parse(Properties cfg, String key, int def) {
            try {
                return Integer.parseInt(cfg.getProperty(key, Integer.toString(def)).trim());
            } catch (NumberFormatException e) {
                return def;
            }
        }

        public boolean isEnabled() {
            return _files > 0 || _percent > 0;
        }
    }

    private static final class State {
        int _lastFiles;
        int _lastPercent;
        long _lastTime;
        long _touched;
    }

    private final Map<String, State> _states = new HashMap<>();

    /**
     * @param key     identifies the release (directory path)
     * @param present files present so far
     * @param total   files expected
     * @param now     current time in milliseconds
     * @return true if an UPDATE should be announced now (and the baseline is advanced)
     */
    public synchronized boolean shouldAnnounce(Settings settings, String key, int present, int total, long now) {
        if (!isDue(settings, key, present, total, now)) {
            return false;
        }
        markAnnounced(key, present, total, now);
        return true;
    }

    /**
     * Like {@link #shouldAnnounce} but leaves the baseline alone, so the caller can first check that
     * the announce can really be sent and then call {@link #markAnnounced}. If files were deleted so
     * that fewer are present than at the last baseline, the baseline is reset to what is present.
     */
    public synchronized boolean isDue(Settings settings, String key, int present, int total, long now) {
        if (!settings.isEnabled() || total <= 0) {
            return false;
        }
        if (present >= total) {
            // complete: the COMPLETE announce takes over, forget the release
            _states.remove(key);
            return false;
        }
        State state = _states.get(key);
        if (state == null) {
            prune();
            state = new State();
            _states.put(key, state);
        }
        state._touched = now;
        int percent = (int) ((present * 100L) / total);
        if (present < state._lastFiles || percent < state._lastPercent) {
            // files were deleted mid-race, do not wait for the count to catch up
            state._lastFiles = Math.min(state._lastFiles, present);
            state._lastPercent = Math.min(state._lastPercent, percent);
            return false;
        }
        boolean due = (settings._files > 0 && present - state._lastFiles >= settings._files)
                || (settings._percent > 0 && percent - state._lastPercent >= settings._percent);
        if (!due) {
            return false;
        }
        // throttled: keep the baseline so the next file after the interval triggers it
        return state._lastTime == 0 || now - state._lastTime >= settings._intervalMillis;
    }

    /** Moves the baseline of the release to the current position, after an UPDATE was announced. */
    public synchronized void markAnnounced(String key, int present, int total, long now) {
        State state = _states.get(key);
        if (state == null || total <= 0) {
            return;
        }
        state._lastFiles = present;
        state._lastPercent = (int) ((present * 100L) / total);
        state._lastTime = now;
    }

    /** Drops the bookkeeping of the least recently touched release when too many are tracked. */
    private void prune() {
        if (_states.size() < MAX_TRACKED) {
            return;
        }
        long oldest = Long.MAX_VALUE;
        String oldestKey = null;
        for (Map.Entry<String, State> e : _states.entrySet()) {
            if (e.getValue()._touched < oldest) {
                oldest = e.getValue()._touched;
                oldestKey = e.getKey();
            }
        }
        if (oldestKey != null) {
            _states.remove(oldestKey);
        }
    }
}
