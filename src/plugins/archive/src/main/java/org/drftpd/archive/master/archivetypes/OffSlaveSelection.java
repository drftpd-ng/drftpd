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
package org.drftpd.archive.master.archivetypes;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * Decision logic of {@link MoveReleaseOffSlavesToMostFreeSlaves}, kept free of any VFS/slave objects
 * (slaves are identified by name) so that it can be unit tested.
 */
final class OffSlaveSelection {

    private OffSlaveSelection() {
    }

    /**
     * @return true if the file has a copy on at least one of the slaves that have to be emptied
     */
    static boolean needsMoving(Collection<String> fileSlaves, Collection<String> offOfSlaves) {
        for (String slave : fileSlaves) {
            if (offOfSlaves.contains(slave)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calculates the destination set of the Job for one file.
     * <p>
     * A finished Job deletes the file from every slave that is NOT in its destination set, so the set has to
     * contain the slaves we want to keep: the free destination slaves plus every slave that already holds the
     * file, except the slaves to move off of. A Job with transferNum == size of this set is only finished when
     * the file is on all of them, which makes the most-free slaves receive a copy.
     *
     * @param fileSlaves  names of the slaves that currently hold the file
     * @param offOfSlaves names of the slaves to move off of
     * @param freeSlaves  names of the most-free destination slaves chosen by the archive type
     * @return the destination slave names, or null if the file is not on an offofslave or there is no
     * free slave to receive it (in which case nothing may be removed)
     */
    static Set<String> jobDestination(Collection<String> fileSlaves, Collection<String> offOfSlaves, Collection<String> freeSlaves) {
        if (!needsMoving(fileSlaves, offOfSlaves) || freeSlaves.isEmpty()) {
            return null;
        }
        Set<String> dest = new HashSet<>(freeSlaves);
        for (String slave : fileSlaves) {
            if (!offOfSlaves.contains(slave)) {
                dest.add(slave);
            }
        }
        // free slaves never are offofslaves (they are excluded when selecting), but make sure
        dest.removeAll(offOfSlaves);
        return dest.isEmpty() ? null : dest;
    }
}
