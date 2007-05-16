package org.drftpd.plugins.sitebot;

//Just a drfptd package

/**
 * Blowfish.java version 1.00.00
 * 
 * Code Written by k2r (k2r.contact@gmail.com)
 * 
 * Tks to Mouser for his precious help.
 * Tks to Murx for correct Padding and Long key.
 * 
 * Use this code is very simple :
 * 
 * Use "Encrypt" with the text to encrypt
 * 		-> The function will encrypt and return the text with +OK at the beginning"
 * 
 * Use "Decrypt" with the text to decrypt
 * 		--> The text must include the +OK or mcps at the front"
 * 
 * To Use Key > 16 char, you must update two jar files in your jre or jdk.
 * 		Java Cryptography Extension (JCE)
 * 		Unlimited Strength Jurisdiction Policy Files 1.4.2
 * 		http://java.sun.com/j2se/1.4.2/download.html#docs
 * Update the two files in jre\lib\security
 * 		-> local_policy.jar
 * 		-> US_export_policy.jar
 * 
 * 
 */

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;

public class Blowfish {

	private static final Logger logger = Logger.getLogger(Blowfish.class);

	private static final String BEGIN = "+OK ";

	private static final String B64 = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private Cipher _ecipher;

	private SecretKeySpec _skeySpec;

	/*
	 * Constructor of Blowfish class Key param
	 */

	public Blowfish(String key) {
		_skeySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
		// Preparing Blowfish mode
		try {
			_ecipher = Cipher.getInstance("Blowfish/ECB/NoPadding");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/* Encrypt function	 */
	public String encrypt(String tocrypt) {

		// Mode cypher in Encrypt mode
		try {
			_ecipher.init(Cipher.ENCRYPT_MODE, _skeySpec);
		} catch (InvalidKeyException e) {
			logger.warn("Invalid blowfish key",e);
		}

		String rEncrypt = "";
		// Paddind the String
		byte[] bEncrypt = tocrypt.getBytes();
		int taille = bEncrypt.length;
		int limit = 8 - (bEncrypt.length % 8);
		byte[] buff = new byte[taille + limit];

		for (int i = 0; i < taille; i++)
			buff[i] = bEncrypt[i];

		for (int i = taille; i < taille + limit; i++)
			buff[i] = 0x0;

		try { 
			// Encrypt the padding string
			byte[] encrypted = _ecipher.doFinal(buff); 
			// B64 ENCRYPTION (mircryption needed)
			rEncrypt = bytetoB64(encrypted);
		} catch (Exception e) {
			logger.warn("Exception whilst encrypting blowfish string",e);
		}

		rEncrypt = BEGIN.concat(rEncrypt);
		return rEncrypt;
	}

	/* Decrypt function */
	public String decrypt(String encrypt) throws UnsupportedEncodingException {

		if(encrypt.startsWith("+OK ")) {encrypt = encrypt.substring(4,encrypt.length());}
		if(encrypt.startsWith("mcps "))	{encrypt = encrypt.substring(5,encrypt.length());}

		// B64 DECRYPTION (mircryption needed)
		byte[] again = b64tobyte(encrypt); 

		byte[] decrypted = null;

		try {
			// Mode cypher in Decrypt mode
			_ecipher.init(Cipher.DECRYPT_MODE, _skeySpec);
			decrypted = _ecipher.doFinal(again);

			// Recup exact length
			int leng = 0;
			while(decrypted[leng] != 0x0) {leng++;}
			byte[] finalArray = new byte[leng];
			// Format & Limit the Result String
			int i = 0;
			while(decrypted[i] != 0x0) {
				finalArray[i] = decrypted[i];
				i++;
			}			
			//Force again the encoding result string
			return new String(finalArray,"8859_1");
		} catch (Exception e) {
			//return e.getMessage();
			// Exception, not necessary padding, return directly
			// The decypted string
			logger.warn("Exception whilst decrypting blowfish string",e);
			return new String(decrypted,"8859_1");
		}
	}

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
