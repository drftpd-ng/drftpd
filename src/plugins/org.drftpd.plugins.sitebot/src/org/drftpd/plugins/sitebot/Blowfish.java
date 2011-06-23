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
import java.util.Arrays; 

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;

import org.apache.log4j.Logger;

public class Blowfish {

	private static final Logger logger = Logger.getLogger(Blowfish.class);

	private static final String BEGIN = "+OK ";

	private Cipher _ecipher;

	private SecretKeySpec _skeySpec;

    private static final char[] CA = "./0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ".toCharArray();  
    private static final int[] IA = new int[256];  
    
    static {  
    	Arrays.fill(IA, -1);  
    	for (int i = 0; i < CA.length; i++) {  
    		IA[CA[i]] = i;  
    	}  
    }  
	
	
	/*
	 * Constructor of Blowfish class Key param
	 */

	public Blowfish(String key) {
		_skeySpec = new SecretKeySpec(key.getBytes(), "Blowfish");
		// Preparing Blowfish mode
		try {
			_ecipher = Cipher.getInstance("Blowfish/ECB/NoPadding");
		} catch (Exception e) {
			logger.error("Failed to init chipher", e); 
		}
	}

	private static byte[] decode(String en) {  
		int right = 0;  
		int left = 0;  
		byte[] dArr = new byte[(en.length() / 12) * 8];  
	
		for (int i = 0, z = 0; z < dArr.length;) {  
			right = IA[en.charAt(i++)] | IA[en.charAt(i++)] << 6 | IA[en.charAt(i++)] << 12 | IA[en.charAt(i++)] << 18 | IA[en.charAt(i++)] << 24 | IA[en.charAt(i++)] << 30;  
			left  = IA[en.charAt(i++)] | IA[en.charAt(i++)] << 6 | IA[en.charAt(i++)] << 12 | IA[en.charAt(i++)] << 18 | IA[en.charAt(i++)] << 24 | IA[en.charAt(i++)] << 30;  
			
			dArr[z++] = (byte) (left >> 24);  
			dArr[z++] = (byte) (left >> 16);  
			dArr[z++] = (byte) (left >> 8);  
			dArr[z++] = (byte) left;  
			
			dArr[z++] = (byte) (right >> 24);  
			dArr[z++] = (byte) (right >> 16);  
			dArr[z++] = (byte) (right >> 8);  
			dArr[z++] = (byte) right;  
		}  
		return dArr;  
	}  
	  
	private static String encode(byte[] ec) {  
		int left = 0;  
		int right = 0;  
		char[] dArr = new char[(ec.length / 8) * 12];  
	
		for(int i = 0, z = 0; i < ec.length;) {  
			left  = (ec[i++] & 0xff) << 24 | (ec[i++] & 0xff) << 16 | (ec[i++] & 0xff) << 8 | (ec[i++] & 0xff);  
			right = (ec[i++] & 0xff) << 24 | (ec[i++] & 0xff) << 16 | (ec[i++] & 0xff) << 8 | (ec[i++] & 0xff);  
	
			dArr[z++] = CA[(right & 0x3f)];  
			dArr[z++] = CA[((right >>  6) & 0x3f)];  
			dArr[z++] = CA[((right >> 12) & 0x3f)];  
			dArr[z++] = CA[((right >> 18) & 0x3f)];  
			dArr[z++] = CA[((right >> 24) & 0x3f)];  
			dArr[z++] = CA[((right >> 30) & 0x3f)];  
	
			dArr[z++] = CA[(left & 0x3f)];  
			dArr[z++] = CA[((left >>  6) & 0x3f)];  
			dArr[z++] = CA[((left >> 12) & 0x3f)];  
			dArr[z++] = CA[((left >> 18) & 0x3f)];  
			dArr[z++] = CA[((left >> 24) & 0x3f)];  
			dArr[z++] = CA[((left >> 30) & 0x3f)];  
		}  
		return new String(dArr);  
	}  
	
	/* Encrypt function	 */
	public String encrypt(String tocrypt) {
		// Make sure _ecipher is synchronized so decrypt/encrypt don't race
		synchronized (_ecipher) {
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
	
			System.arraycopy(bEncrypt, 0, buff, 0, taille); 
	
			for (int i = taille; i < taille + limit; i++)
				buff[i] = 0x0;
	
			try { 
				// Encrypt the padding string
				byte[] encrypted = _ecipher.doFinal(buff); 
				// B64 ENCRYPTION (mircryption needed)
				rEncrypt = encode(encrypted);
			} catch (Exception e) {
				logger.warn("Exception whilst encrypting blowfish string",e);
			}
	
			rEncrypt = BEGIN.concat(rEncrypt);
			return rEncrypt;
		}
	}

	/* Decrypt function */
	public String decrypt(String encrypt) throws UnsupportedEncodingException {

        if(encrypt.startsWith("+OK ")) {  
        	encrypt = encrypt.substring(4, encrypt.length());  
        }  
        if(encrypt.startsWith("mcps ")) {  
        	encrypt = encrypt.substring(5, encrypt.length());  
        }  

		// B64 DECRYPTION (mircryption needed)
		byte[] again = decode(encrypt);  

		byte[] decrypted = null;

		// Make sure _ecipher is synchronized so decrypt/encrypt don't race
		synchronized (_ecipher) {
			try {
				// Mode cypher in Decrypt mode
				_ecipher.init(Cipher.DECRYPT_MODE, _skeySpec);
				decrypted = _ecipher.doFinal(again);
	
				// Recup exact length
				int leng = decrypted.length - 8;
				while(leng < decrypted.length && decrypted[leng] != 0x0) {
					leng++;
				}
				byte[] finalArray = new byte[leng];
				
				System.arraycopy(decrypted, 0, finalArray, 0, leng); 
				
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
	
}
