/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.util;

import java.io.UnsupportedEncodingException;

import org.apache.log4j.Logger;

/**
 * @author djb61
 * @version $Id$
 */
public class Base64 {

	private static final Logger logger = Logger.getLogger(Base64.class);

	private static final String B64 = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	public static byte[] b64tobyte(String ec) {

		String dc = "";

		int k = -1;
		while (k < (ec.length() - 1)) {

			int right = 0;
			int left = 0;
			int v = 0;
			int w = 0;
			int z = 0;

			for (int i = 0; i < 6; i++) {
				k++;
				v = B64.indexOf(ec.charAt(k));
				right |= v << (i * 6);
			}

			for (int i = 0; i < 6; i++) {
				k++;
				v = B64.indexOf(ec.charAt(k));
				left |= v << (i * 6);
			}

			for (int i = 0; i < 4; i++) {
				w = ((left & (0xFF << ((3 - i) * 8))));
				z = w >> ((3 - i) * 8);
				if (z < 0) {
					z = z + 256;
				}
				dc += (char) z;
			}

			for (int i = 0; i < 4; i++) {
				w = ((right & (0xFF << ((3 - i) * 8))));
				z = w >> ((3 - i) * 8);
				if (z < 0) {
					z = z + 256;
				}
				dc += (char) z;
			}
		}

		byte[] result = new byte[1024];
		try {
			// Force the encoding result string
			result = dc.getBytes("8859_1");
		} catch (UnsupportedEncodingException e) {
			// Shouldn't be possible as this is a JVM default charset
			logger.debug("Couldn't use 8859_1 charset",e);
		}
		return result;
	}

	public static String bytetoB64(byte[] ec) {

		String dc = "";

		int left = 0;
		int right = 0;
		int k = -1;
		int v;

		while (k < (ec.length - 1)) {
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			left = v << 24;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			left += v << 16;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			left += v << 8;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			left += v;

			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			right = v << 24;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			right += v << 16;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			right += v << 8;
			k++;
			v = ec[k];
			if (v < 0)
				v += 256;
			right += v;

			for (int i = 0; i < 6; i++) {
				dc += B64.charAt(right & 0x3F);
				right = right >> 6;
			}

			for (int i = 0; i < 6; i++) {
				dc += B64.charAt(left & 0x3F);
				left = left >> 6;
			}
		}
		return dc;
	}
}
