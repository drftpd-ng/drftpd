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
import org.drftpd.util.Base64;

public class Blowfish {

	private static final Logger logger = Logger.getLogger(Blowfish.class);

	private static final String BEGIN = "+OK ";

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
			rEncrypt = Base64.bytetoB64(encrypted);
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
		byte[] again = Base64.b64tobyte(encrypt); 

		byte[] decrypted = null;

		try {
			// Mode cypher in Decrypt mode
			_ecipher.init(Cipher.DECRYPT_MODE, _skeySpec);
			decrypted = _ecipher.doFinal(again);

			// Recup exact length
			int leng = 0;
			while(decrypted[leng] != 0x0 && leng < (decrypted.length - 1)) {leng++;}
			byte[] finalArray = new byte[leng];
			// Format & Limit the Result String
			for(int i = 0; i < leng; i++) {
				finalArray[i] = decrypted[i];
			}			
			//Force again the encoding result string
			return new String(finalArray,"8859_1");
		} catch (InvalidKeyException e) {
			logger.error("Invalid key error when decrypting blowfish string, possibly means export crypto isn't installed",e);
			return "";
		} catch (Exception e) {
			// Exception, not necessary padding, return directly
			// The decypted string
			logger.warn("Exception whilst decrypting blowfish string",e);
			return new String(decrypted,"8859_1");
		}
	}

	
}
