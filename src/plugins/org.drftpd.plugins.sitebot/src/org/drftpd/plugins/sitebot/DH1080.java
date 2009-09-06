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
package org.drftpd.plugins.sitebot;

import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

import org.apache.log4j.Logger;

/**
 * @author djb61
 * @version $Id$
 */
public class DH1080 {

	private static final Logger logger = Logger.getLogger(DH1080.class);

	private static final String B64 = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

	private static final String PRIME = "++ECLiPSE+is+proud+to+present+latest+FiSH+release+featuring+even+more+security+for+you+++shouts+go+out+to+TMG+for+helping+to+generate+this+cool+sophie+germain+prime+number++++/C32L";

	private BigInteger _privateInt;

	private BigInteger _publicInt;

	public DH1080() {
		try {
			SecureRandom sRNG = SecureRandom.getInstance("SHA1PRNG");
			_privateInt = new BigInteger(1080, sRNG);
			BigInteger primeInt = new BigInteger(1,decodeB64(PRIME));
			_publicInt = (new BigInteger("2")).modPow(_privateInt, primeInt);
		} catch (NoSuchAlgorithmException e) {
			logger.debug("Algorithm for DH1080 random number generator not available",e);
		}
	}

	public String getPublicKey() {
		return encodeB64(getBytes(_publicInt));
	}

	public String getSharedSecret(String peerPubKey) {
		BigInteger primeInt = new BigInteger(1,decodeB64(PRIME));
		BigInteger peerPubInt = new BigInteger(1,decodeB64(peerPubKey));
		BigInteger shareInt = peerPubInt.modPow(_privateInt, primeInt);
		try {
			MessageDigest md = MessageDigest.getInstance("SHA-256");
			byte[] hashed = md.digest(getBytes(shareInt));
			return encodeB64(hashed);
		} catch (NoSuchAlgorithmException e) {
			logger.debug("Algorithm for DH1080 shared secret hashing not available",e);
		}
		return null;
	}

	/** By default BigInteger.toByteArray() returns bytes including a sign
	 * bit, since our numbers are always positive this bit is always zero
	 * so not too much of a problem. However if the number is an exact
	 * multiple of 8 bits then the addition of a sign bit will cause an
	 * extra byte to be added to the resulting array. This will cause problems
	 * so in this case we strip the first byte off the array before returning.
	 */
	private byte[] getBytes(BigInteger big) {
		byte[] bigBytes = big.toByteArray();
		if ((big.bitLength() % 8) != 0) {
			return bigBytes;
		}
		else {
			byte[] smallerBytes = new byte[big.bitLength() / 8];
			System.arraycopy(bigBytes, 1, smallerBytes, 0, smallerBytes.length);
			return smallerBytes;
		}
	}

	/** This is an alternate base64 decoder, this is required as
	 * the DH1080 implementation used by everyone relies on a non
	 * RFC compliant base64 implementation, therefore the other
	 * base64 methods in the blowfish class cannot be used. This method
	 * implements the broken version used by DH1080 and should
	 * not be used for anything else.
	 */
	private static byte[] decodeB64(String input) {
		StringBuilder outputBuilder = new StringBuilder();
		int k = 0;
		int overflow;
		byte temp;
		while (true) {
			if (k+1<input.length()) {
				temp = (byte)(B64.indexOf(input.charAt(k))<<2);
				k++;
				temp |= B64.indexOf(input.charAt(k))>>4;
				overflow = temp;
				if (overflow < 0) {
					overflow += 256;
				}
				outputBuilder.append((char)overflow);
			}
			else {
				break;
			}
			if (k+1<input.length()) {
				temp = (byte)(B64.indexOf(input.charAt(k))<<4);
				k++;
				temp |= B64.indexOf(input.charAt(k))>>2;
				overflow = temp;
				if (overflow < 0) {
					overflow += 256;
				}
				outputBuilder.append((char)overflow);
			}
			else {
				break;
			}
			if (k+1<input.length()) {
				temp = (byte)(B64.indexOf(input.charAt(k))<<6);
				k++;
				temp |= B64.indexOf(input.charAt(k));
				overflow = temp;
				if (overflow < 0) {
					overflow += 256;
				}
				outputBuilder.append((char)overflow);
			}
			else {
				break;
			}
			k++;
		} try {
			return outputBuilder.toString().getBytes("8859_1");
		} catch (UnsupportedEncodingException e) {
			// Shouldn't be possible as this is a JVM default charset
			logger.warn("Couldn't use 8859_1 charset",e);
			return outputBuilder.toString().getBytes();
		}
	}

	/** This is an alternate base64 encoder, this is required as
	 * the DH1080 implementation used by everyone relies on a non
	 * RFC compliant base64 implementation, therefore the other
	 * base64 methods in the blowfish class cannot be used. This method
	 * implements the broken version used by DH1080 and should
	 * not be used for anything else.
	 */
	private static String encodeB64(byte[] input) {
		int i;
		char m,t;
		StringBuilder outputBuilder = new StringBuilder();

		m=0x80;
		for (i=0,t=0; i<(input.length<<3); i++){
			if ((input[(i>>3)]&m) != 0) {
				t|=1;
			}
			if (((int)(m>>=1)) == 0) {
				m=0x80;
			}
			if (((i+1)%6) == 0) {
				outputBuilder.append(B64.charAt(t));
				t&=0;
			}
			t<<=1;
		}
		m=(char)(5-(i%6));
		t<<=m;
		if (((int)m) != 0) {
			outputBuilder.append(B64.charAt(t));
		}
		return outputBuilder.toString();
	}
}
