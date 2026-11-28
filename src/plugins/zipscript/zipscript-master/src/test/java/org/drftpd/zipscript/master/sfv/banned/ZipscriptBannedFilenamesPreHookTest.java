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

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ZipscriptBannedFilenamesPreHookTest {

    @AfterEach
    void reset() {
        ZipscriptBannedFilenamesPreHook.setBanned(BannedFilenames.fromProperties(new Properties()));
    }

    @Test
    void sharedListDecidesIsBanned() {
        assertFalse(ZipscriptBannedFilenamesPreHook.isBanned("/TV/Rel-GRP", "Thumbs.db"));
        Properties cfg = new Properties();
        cfg.setProperty("banned.filenames", "thumbs.db,*.url");
        ZipscriptBannedFilenamesPreHook.setBanned(BannedFilenames.fromProperties(cfg));
        assertTrue(ZipscriptBannedFilenamesPreHook.isBanned("/TV/Rel-GRP", "Thumbs.db"));
        assertTrue(ZipscriptBannedFilenamesPreHook.isBanned("/TV/Rel-GRP", "x.url"));
        assertFalse(ZipscriptBannedFilenamesPreHook.isBanned("/TV/Rel-GRP", "extra.bin"));
    }
}
