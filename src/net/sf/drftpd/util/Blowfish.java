package net.sf.drftpd.util;

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
 * 		-> The function will encrypt and return the text with +OK at the biginning"
 * 
 * Use "Decrypt" with the text to decrypt
 * 		--> The text must include the +OK or mcps at the front"
 * 
 * There are a good exemple in "Main" function
 * 
 * To Use Key > 16 char, you must update two jar files in your jre or jdk.
 * 		Java Cryptography Extension (JCE)
 * 		Unlimited Strength Jurisdiction Policy Files 1.4.2
 * 		http://java.sun.com/j2se/1.4.2/download.html#docs
 * Update the two files in jre\lib\security
 * 		-> local_policy.jar
 * 		-> US_export_policy.jar
 * 
 */

import java.io.UnsupportedEncodingException;
import java.security.InvalidKeyException;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

public class Blowfish {

	/*
	 * Constructor of Blowfish class Key param
	 */

	public Blowfish(String key) {
		skeySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
		// Preparing Blowfish mode
		try {
			ecipher = Cipher.getInstance("Blowfish/ECB/NoPadding");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/* Encrypt function */
	public String Encrypt(String tocrypt) {

		// Mode cypher in Encrypt mode
		try {
			ecipher.init(Cipher.ENCRYPT_MODE, skeySpec);
		} catch (InvalidKeyException e) {
			e.printStackTrace();
		}

		String REncrypt = "";
		// Paddind the String
		byte[] BEncrypt = tocrypt.getBytes();
		int Taille = BEncrypt.length;
		int Limit = 8 - (BEncrypt.length % 8);
		byte[] buff = new byte[Taille + Limit];

		for (int i = 0; i < Taille; i++)
			buff[i] = BEncrypt[i];

		for (int i = Taille; i < Taille + Limit; i++)
			buff[i] = 0x0;

		try {
			// Encrypt the padding string
			byte[] encrypted = ecipher.doFinal(buff);
			// B64 ENCRYPTION (mircryption needed)
			REncrypt = bytetoB64(encrypted);
		} catch (Exception e) {
			e.printStackTrace();
		}

		REncrypt = Begin.concat(REncrypt);
		return REncrypt;
	}

	/* Decrypt function */
	public String Decrypt(String encrypt) throws UnsupportedEncodingException {

		if (encrypt.startsWith("+OK ")) {
			encrypt = encrypt.substring(4, encrypt.length());
		}
		if (encrypt.startsWith("mcps ")) {
			encrypt = encrypt.substring(5, encrypt.length());
		}

		// B64 DECRYPTION (mircryption needed)
		byte[] Again = B64tobyte(encrypt);

		byte[] decrypted = null;

		try {
			// Mode cypher in Decrypt mode
			ecipher.init(Cipher.DECRYPT_MODE, skeySpec);
			decrypted = ecipher.doFinal(Again);

			// Recup exact length
			int leng = 0;
			while (decrypted[leng] != 0x0) {
				leng++;
			}
			byte[] Final = new byte[leng];
			// Format & Limit the Result String
			int i = 0;
			while (decrypted[i] != 0x0) {
				Final[i] = decrypted[i];
				i++;
			}
			// Force again the encoding result string
			return new String(Final, "8859_1");
		} catch (Exception e) {
			// return e.getMessage();
			// Exception, not necessary padding, return directly
			// The decypted string
			return new String(decrypted, "8859_1");
		}
	}

	public static byte[] B64tobyte(String ec) {

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

		byte[] Result = new byte[1024];
		try {
			// Force the encoding result string
			Result = dc.getBytes("8859_1");
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return Result;
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

	private static String Begin = "+OK ";

	private static String B64 = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";

	private Cipher ecipher;

	private SecretKeySpec skeySpec;
}
