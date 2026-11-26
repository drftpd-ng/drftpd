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
package org.drftpd.zipscript.master.release;

/**
 * Decision logic of the 'downloadinc' and 'deletecomplete' path rules (#595), kept free of any VFS or
 * slave access so it can be unit tested.
 */
public final class ReleaseRules {

    /** perms.conf directive: deny RETR while the release is incomplete. */
    public static final String DOWNLOAD_INCOMPLETE = "downloadinc";

    /** perms.conf directive: deny DELE/WIPE inside a complete release. */
    public static final String DELETE_COMPLETE = "deletecomplete";

    /** How many dirs are looked at (the file's own dir and its parents) to find the release dir. */
    public static final int MAX_LEVELS = 3;

    private ReleaseRules() {
    }

    public enum State {
        COMPLETE,
        INCOMPLETE,
        /** Status could not be determined (slave offline, unreadable sfv...). Rules never act on it. */
        UNKNOWN
    }

    public enum Kind {SFV, ZIP}

    /** A directory as seen by {@link #locate}. */
    public interface ReleaseDir {
        /** @return SFV if the dir holds an .sfv, ZIP if it holds .zip files (and no .sfv), null for neither. */
        Kind kind();

        /** @return the parent dir, or null for the root */
        ReleaseDir parent();
    }

    /**
     * Finds the release dir for a file whose parent directory is {@code start}: the dir holding the .sfv
     * (or the .zip files), or else the nearest parent that does (e.g. the file is in CD1 and the sfv is
     * one level up). At most {@link #MAX_LEVELS} dirs are looked at.
     *
     * @return the release dir, or null if there is none
     */
    public static ReleaseDir locate(ReleaseDir start) {
        ReleaseDir dir = start;
        for (int i = 0; i < MAX_LEVELS && dir != null; i++) {
            if (dir.kind() != null) {
                return dir;
            }
            dir = dir.parent();
        }
        return null;
    }

    /**
     * @param total   number of files the release should have (sfv entries, or DIZ total)
     * @param missing number of those files that are missing
     */
    public static State stateOf(int total, int missing) {
        if (total <= 0) {
            // an empty sfv or a bogus DIZ says nothing about completeness
            return State.UNKNOWN;
        }
        return missing <= 0 ? State.COMPLETE : State.INCOMPLETE;
    }

    /**
     * @param exempt true if the user is allowed by the 'downloadinc' line matching the path, or no line matches
     */
    public static boolean denyDownload(State state, boolean exempt) {
        return state == State.INCOMPLETE && !exempt;
    }

    /**
     * @param exempt true if the user is allowed by the 'deletecomplete' line matching the path, or no line matches
     */
    public static boolean denyDelete(State state, boolean exempt) {
        return state == State.COMPLETE && !exempt;
    }
}
