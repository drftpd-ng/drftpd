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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;

/**
 * @author djb61
 * @version $Id$
 */
public class DH1080 {

	private static final Logger logger = LogManager.getLogger(DH1080.class);

	private static final char[] CA = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".toCharArray();

	private static final int[] IA = new int[256];  
	 
	static {  
		Arrays.fill(IA, -1);  
		for (int i = 0; i < CA.length; i++) {  
			IA[CA[i]] = i;  
		}  
	}  
	
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
		byte[] smallerBytes = new byte[big.bitLength() / 8];
		System.arraycopy(bigBytes, 1, smallerBytes, 0, smallerBytes.length);
		return smallerBytes;
	}

	/** This is an alternate base64 decoder, this is required as
	 * the DH1080 implementation used by everyone relies on a non
	 * RFC compliant base64 implementation, therefore the other
	 * base64 methods in the blowfish class cannot be used. This method
	 * implements the broken version used by DH1080 and should
	 * not be used for anything else.
	 */
	private static byte[] decodeB64(String input) {
		byte[] dArr = new byte[input.length() * 6 >> 3];  
		  
		for(int i = 0, z = 0; z < dArr.length;) {  
			if (z >= dArr.length) {  
				break;
			}
			dArr[z++] = (byte) ((IA[input.charAt(i)] << 2) | (IA[input.charAt(i + 1)] >> 4));  
			i++;  
			if (z >= dArr.length) { 
				break;
			}
			dArr[z++] = (byte) ((IA[input.charAt(i)] << 4) | (IA[input.charAt(i + 1)] >> 2));  
			i++;  
			if (z >= dArr.length) {  
				break;
			}
			dArr[z++] = (byte) ((IA[input.charAt(i)] << 6) | IA[input.charAt(i + 1)]);  
			i += 2; 
		}
		return dArr;
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
		int p = 0;  
		char m, t;  
		char[] dArr = new char[((input.length / 3 + 1) << 2) - (3 - input.length % 3)];  

		m = 0x80;  
		for (i = 0, t = 0; i < (input.length << 3); i++){  
			if ((input[(i >> 3)] & m) != 0) {  
				t |= 1; 
			}
			if ((m>>=1) == 0) {
				m=0x80;
			}
			if (((i + 1) % 6) == 0) {  
				dArr[p++] = CA[t];  
				t &= 0;  
			}  
			
			t<<=1;
		}
		m=(char)(5-(i%6));
		t<<=m;
		if (m != 0) {
			dArr[p++] = CA[t];
		}
		return new String(dArr);
	}
}
