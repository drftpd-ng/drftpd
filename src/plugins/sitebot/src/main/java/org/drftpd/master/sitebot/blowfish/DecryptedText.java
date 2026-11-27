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
package org.drftpd.master.sitebot.blowfish;

/**
 * Text of a decrypted line
 *
 * @param text the decrypted text
 * @param cut  true if the encrypted line was found to be cut and only its complete blocks were
 *             decrypted. Best effort: a line cut exactly at the end of an 8 byte block (every
 *             12 characters in ECB, every 10 or 11 base64 characters in CBC, for example 32)
 *             decodes cleanly and is not flagged, so a cut line is usually, not always, detected
 */
public record DecryptedText(String text, boolean cut) {
}
