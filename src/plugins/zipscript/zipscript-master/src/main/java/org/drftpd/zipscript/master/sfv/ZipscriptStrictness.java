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

import java.util.Collection;
import java.util.Properties;

/**
 * Decisions for the optional strictness settings in zipscript.conf (all off by default).
 * Kept free of VFS access so the decisions can be unit tested.
 */
public final class ZipscriptStrictness {

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
        return null;
    }
}
