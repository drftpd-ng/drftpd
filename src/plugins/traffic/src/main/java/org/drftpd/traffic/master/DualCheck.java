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
package org.drftpd.traffic.master;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Pure detection logic for "dualing": one user running several simultaneous
 * transfers inside the same release directory, each above a minimum speed.
 * It has no dependency on the master so it can be unit tested.
 */
public final class DualCheck {

    /** Transfers younger than this have no meaningful speed yet and are ignored. */
    public static final long MIN_ELAPSED_MS = 3000;

    private DualCheck() {
    }

    /** A snapshot of one active transfer. {@code handle} is opaque to the detection. */
    public static final class Sample {
        private final String _user;
        private final String _release;
        private final boolean _stor;
        private final long _speed;
        private final long _elapsedMs;
        private final Object _handle;

        public Sample(String user, String release, boolean stor, long speed, long elapsedMs, Object handle) {
            _user = user;
            _release = release;
            _stor = stor;
            _speed = speed;
            _elapsedMs = elapsedMs;
            _handle = handle;
        }

        public String getUser() {
            return _user;
        }

        public String getRelease() {
            return _release;
        }

        public boolean isStor() {
            return _stor;
        }

        public long getSpeed() {
            return _speed;
        }

        public long getElapsedMs() {
            return _elapsedMs;
        }

        public Object getHandle() {
            return _handle;
        }
    }

    /** A user/release/direction with at least {@code limit} qualifying transfers. */
    public static final class Violation {
        private final String _user;
        private final String _release;
        private final boolean _stor;
        private final List<Sample> _samples;
        private final Sample _target;

        Violation(String user, String release, boolean stor, List<Sample> samples, Sample target) {
            _user = user;
            _release = release;
            _stor = stor;
            _samples = samples;
            _target = target;
        }

        public String getUser() {
            return _user;
        }

        public String getRelease() {
            return _release;
        }

        public boolean isStor() {
            return _stor;
        }

        public List<Sample> getSamples() {
            return _samples;
        }

        /** The most recently started transfer of the group: the one the action is applied to. */
        public Sample getTarget() {
            return _target;
        }
    }

    /**
     * @param minSpeed minimum speed in bytes/s; must be positive, otherwise nothing is detected
     * @param limit    number of simultaneous qualifying transfers that triggers (values below 2 are treated as 2)
     * @param up       consider uploads
     * @param dn       consider downloads
     * @param exempt   transfers for which this returns true are ignored (may be null)
     */
    public static List<Violation> detect(List<Sample> samples, long minSpeed, int limit, boolean up, boolean dn,
                                         Predicate<Sample> exempt) {
        List<Violation> result = new ArrayList<>();
        if (samples == null || minSpeed <= 0 || (!up && !dn)) {
            return result;
        }
        int needed = Math.max(2, limit);

        Map<String, List<Sample>> groups = new LinkedHashMap<>();
        for (Sample s : samples) {
            if (s.getUser() == null || s.getRelease() == null) {
                continue;
            }
            if (s.isStor() ? !up : !dn) {
                continue;
            }
            if (s.getSpeed() < minSpeed || s.getElapsedMs() < MIN_ELAPSED_MS) {
                continue;
            }
            if (exempt != null && exempt.test(s)) {
                continue;
            }
            String key = (s.isStor() ? "U" : "D") + '\u0000' + s.getUser() + '\u0000' + s.getRelease();
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(s);
        }

        for (List<Sample> group : groups.values()) {
            if (group.size() >= needed) {
                Sample first = group.get(0);
                Sample target = group.stream().min(Comparator.comparingLong(Sample::getElapsedMs)).get();
                result.add(new Violation(first.getUser(), first.getRelease(), first.isStor(), group, target));
            }
        }
        return result;
    }
}
