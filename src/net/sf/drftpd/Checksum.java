/*
 * Created on 2003-sep-29
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class Checksum {

	public static String formatChecksum(long checkSum) {
		String checksumString = Long.toHexString(checkSum);
		return "00000000".substring(0, 8 - checksumString.length()).concat(checksumString);	
	}
}
