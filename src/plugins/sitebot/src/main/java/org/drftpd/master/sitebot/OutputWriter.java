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
package org.drftpd.master.sitebot;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * @author djb61
 * @version $Id$
 */
public class OutputWriter {

    private BlowfishManager _cipher;

    private boolean _blowfishEnabled;

    private SiteBot _bot;

    private String _output;

    private int _maxOutputLen;

    protected OutputWriter() {
        // Empty constructor for extending classes
    }

    public OutputWriter(SiteBot bot, String output, BlowfishManager cipher) {
        _bot = bot;
        _output = output;
        _cipher = cipher;
        _blowfishEnabled = _bot.getConfig().getBlowfishEnabled();
        _maxOutputLen = getMaxLineLength();
    }

    public synchronized void sendMessage(String message) {
        for (String line : splitLines(message)) {
            if (_blowfishEnabled) {
                // Check if we have a valid cipher before proceeding, this is to cover
                // the case where the OutputWriter is for a private message to a user
                // yet they don't have a blowfish key available. This is possible since
                // they could've initiated the command using blowfish in a channel. If
                // this is the case just skip the output to them.
                if (_cipher != null) {
                    String encrypted = _cipher.encrypt(line);
                    // Never fall back to plain text, the error is already logged
                    if (encrypted != null) {
                        _bot.sendMessage(_output, encrypted);
                    }
                }
            } else {
                _bot.sendMessage(_output, line);
            }
        }
    }

    public void reload() {
        _blowfishEnabled = _bot.getConfig().getBlowfishEnabled();
        _maxOutputLen = getMaxLineLength();
    }

    protected void updateCipher(BlowfishManager cipher) {
        _cipher = cipher;
    }

    protected String getDestination() {
        return _output;
    }

    private List<String> splitLines(String message) {
        int maxBytes = _maxOutputLen;
        if (_blowfishEnabled && _cipher != null) {
            maxBytes = _cipher.maxPlaintextBytes(_maxOutputLen);
        }
        return splitUtf8(message, maxBytes);
    }

    /**
     * Room left for the message text in a raw line
     * ":hostmask PRIVMSG target :text\r\n", in bytes.
     */
    private int getMaxLineLength() {
        return _bot.getConfig().getMaxLineLength() - utf8Length(_bot.getHostMask())
                - utf8Length(_output) - 14;
    }

    /**
     * Split a message into lines of at most maxBytes bytes in UTF-8, at the last
     * space when there is one, never inside a character.
     */
    static List<String> splitUtf8(String message, int maxBytes) {
        List<String> lines = new ArrayList<>();
        // Too small to hold any character, send as is
        if (maxBytes < 4) {
            lines.add(message);
            return lines;
        }
        String rest = message;
        while (utf8Length(rest) > maxBytes) {
            // Longest prefix that fits
            int bytes = 0;
            int end = 0;
            while (end < rest.length()) {
                int cp = rest.codePointAt(end);
                int len = cp < 0x80 ? 1 : cp < 0x800 ? 2 : cp < 0x10000 ? 3 : 4;
                if (bytes + len > maxBytes) {
                    break;
                }
                bytes += len;
                end += Character.charCount(cp);
            }
            int space = rest.lastIndexOf(' ', end);
            if (space > 0) {
                lines.add(rest.substring(0, space));
                rest = rest.substring(space + 1);
            } else {
                lines.add(rest.substring(0, end));
                rest = rest.substring(end);
            }
        }
        lines.add(rest);
        return lines;
    }

    private static int utf8Length(String text) {
        return text.getBytes(StandardCharsets.UTF_8).length;
    }
}
