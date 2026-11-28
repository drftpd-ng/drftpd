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
package org.drftpd.zipscript.master.mp3.vfs;

import org.drftpd.zipscript.common.mp3.MP3Info;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class ZipscriptVFSDataMP3Test {

    @Test
    void untaggedTrackIsJudgedByTheAlbumInfoWhenThereIsOne() {
        MP3Info album = new MP3Info();
        MP3Info untagged = new MP3Info();
        assertSame(album, ZipscriptVFSDataMP3.pickForRules(album, untagged));
        assertSame(album, ZipscriptVFSDataMP3.pickForRules(album, null));
    }

    @Test
    void untaggedTrackUsesItsOwnInfoWithoutAlbumInfo() {
        MP3Info untagged = new MP3Info();
        assertSame(untagged, ZipscriptVFSDataMP3.pickForRules(null, untagged));
        assertNull(ZipscriptVFSDataMP3.pickForRules(null, null));
    }
}
