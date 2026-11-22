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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.function.BooleanSupplier;

/**
 * Decides whether a release may be archived according to the skipincomplete / skipnuked options.
 * <p>
 * This class has no dependency on the VFS or on the zipscript plugin (see {@link SfvDirectoryNode} for the
 * zipscript backed implementation), so the zipscript classes are only loaded when skipincomplete is enabled.
 */
final class ReleaseCompleteness {

    private static final Logger logger = LogManager.getLogger(ReleaseCompleteness.class);

    private ReleaseCompleteness() {
    }

    /**
     * A directory of a release.
     */
    interface Node {
        /**
         * @return TRUE if the directory has an SFV and all its files are present, FALSE if the SFV is
         * not complete, or null if the directory has no SFV at all
         * @throws Exception if the state cannot be determined (slave offline, unreadable SFV, ...)
         */
        Boolean sfvFinished() throws Exception;

        /**
         * @return the sub directories of this directory (CD1, CD2, Sample, ...)
         */
        Collection<? extends Node> children() throws FileNotFoundException;
    }

    /**
     * A release is complete if every directory in it that has an SFV is finished according to the zipscript
     * (SFVStatus.isFinished()). Directories without an SFV (Sample, Proof, or a release without any SFV at all)
     * do not make the release incomplete. If the state cannot be determined the release is NOT considered
     * complete, it is looked at again on the next cycle.
     */
    static boolean isComplete(Node dir) {
        try {
            Boolean finished = dir.sfvFinished();
            if (finished == null) {
                logger.debug("{} has no SFV, treating it as complete", dir);
            } else if (!finished) {
                logger.debug("{} has an incomplete SFV", dir);
                return false;
            }
            for (Node child : dir.children()) {
                if (!isComplete(child)) {
                    return false;
                }
            }
            return true;
        } catch (FileNotFoundException e) {
            // directory is gone, nothing to archive here
            logger.debug("{} not found while checking completeness, treating it as complete", dir);
            return true;
        } catch (Exception e) {
            logger.debug("Unable to determine if {} is complete, treating it as incomplete", dir, e);
            return false;
        }
    }

    /**
     * @return true if the release has to be left out of this cycle. The (more expensive) checks are only
     * evaluated when the matching option is enabled, nuked first.
     */
    static boolean shouldSkip(boolean skipNuked, boolean skipIncomplete, BooleanSupplier nuked, BooleanSupplier complete) {
        if (skipNuked && nuked.getAsBoolean()) {
            return true;
        }
        return skipIncomplete && !complete.getAsBoolean();
    }

    /**
     * Parses a boolean option, accepts true/false, yes/no, on/off and 1/0 (case insensitive).
     * A missing, blank or unrecognised value gives the default.
     */
    static boolean parseFlag(String value, boolean defaultValue, String key) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        switch (value.trim().toLowerCase()) {
            case "true":
            case "yes":
            case "on":
            case "1":
                return true;
            case "false":
            case "no":
            case "off":
            case "0":
                return false;
            default:
                logger.error("{} '{}' is not a valid boolean, using {}", key, value.trim(), defaultValue);
                return defaultValue;
        }
    }
}
