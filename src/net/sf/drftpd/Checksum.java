package net.sf.drftpd;

/**
 * @author mog
 *
 * @version $Id: Checksum.java,v 1.2 2003/12/23 13:38:18 mog Exp $
 */
public class Checksum {

	public static String formatChecksum(long checkSum) {
		String checksumString = Long.toHexString(checkSum);
		return "00000000".substring(0, 8 - checksumString.length()).concat(checksumString);	
	}
}
