/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.zipscript.mp3.slave.decoder;

/**
 * 16-Bit CRC checksum
 * @author Originally taken from JLayer - http://www.javazoom.net/javalayer/javalayer.html
 * @version $Id$
 */
public final class Crc16 {
	private static	short polynomial=(short)0x8005;
	private			short crc;

	/**
	 * Dummy Constructor
	 */
	public Crc16() { 
		crc = (short) 0xFFFF;
	}

	/**
	 * Feed a bitstring to the crc calculation (0 < length <= 32).
	 */
	public void add_bits (int bitstring, int length) {
		int bitmask = 1 << (length - 1);
		do
			if (((crc & 0x8000) == 0) ^ ((bitstring & bitmask) == 0 )) {
				crc <<= 1;
				crc ^= polynomial;
			}
			else {
				crc <<= 1;
			}
		while ((bitmask >>>= 1) != 0);
	}

	/**
	 * Return the calculated checksum.
	 * Erase it for next calls to add_bits().
	 */
	public short	checksum() {
		short sum = crc;
		crc = (short) 0xFFFF;
		return sum;
	}
}
