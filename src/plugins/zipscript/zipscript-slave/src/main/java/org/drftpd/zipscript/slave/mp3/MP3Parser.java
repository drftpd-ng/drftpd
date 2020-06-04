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
package org.drftpd.zipscript.slave.mp3;

import org.drftpd.zipscript.common.mp3.MP3Info;
import org.drftpd.zipscript.slave.mp3.decoder.Bitstream;
import org.drftpd.zipscript.slave.mp3.decoder.BitstreamException;
import org.drftpd.zipscript.slave.mp3.decoder.Header;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

/**
 * @author djb61
 * @version $Id$
 */
public class MP3Parser {

    private final File _mp3file;

    public MP3Parser(File mp3file) {
        _mp3file = mp3file;
    }

    public MP3Info getMP3Info() throws IOException {
        FileInputStream in = null;
        Header frameHeader = null;
        try {
            in = new FileInputStream(_mp3file);
            Bitstream mp3Stream = new Bitstream(in);
            // Read 4 frames to ensure this really is an mp3
            for (int i = 0; i < 4; i++) {
                try {
                    frameHeader = mp3Stream.readFrame();
                } catch (BitstreamException e) {
                    // Not a valid MP3
                    throw new IOException(_mp3file.getName() + " is not a valid MP3 file");
                } finally {
                    if (mp3Stream != null) {
                        try {
                            mp3Stream.close();
                        } catch (BitstreamException e) {
                            // ignore
                        }
                    }
                }
                if (frameHeader == null) {
                    // Not a valid MP3
                    throw new IOException(_mp3file.getName() + " is not a valid MP3 file");
                }
            }
        } finally {
            if (in != null) {
                in.close();
            }
        }
        MP3Info mp3info = new MP3Info();
        mp3info.setBitrate(frameHeader.bitrate());
        mp3info.setSamplerate(frameHeader.sample_frequency_string());
        mp3info.setEncodingtype(frameHeader.vbr() ? "VBR" : "CBR");
        mp3info.setStereoMode(frameHeader.mode_string());
        mp3info.setRuntime(frameHeader.total_ms((int) _mp3file.length()));

        // Get ID3 tag
        ID3Parser id3parser = new ID3Parser(_mp3file, "r");
        mp3info.setID3Tag(id3parser.getID3Tag());
        id3parser.close();

        return mp3info;
    }
}
