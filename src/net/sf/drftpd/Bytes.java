/*
 * Created on 2003-aug-08
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd;

import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class Bytes {
	//yotta
	//zetta
	//1,000,000,000,000,000,000 = exa
	public static long EXA = 1000000000000000000L;
	//1,000,000,000,000,000 = peta
	public static long PETA = 1000000000000000L;
	//1,000,000,000,000 = terra
	public static long TERRA = 1000000000000L;
	//1,000,000,000 GB
	public static long GIGA = 1000000000L;
	//1,000,000 MB
	public static long MEGA = 1000000L;
	//1,000 KB
	public static long KILO = 1000L;

	public static String formatBytes(long bytes) {
		DecimalFormatSymbols formatsymbols = new DecimalFormatSymbols();
		formatsymbols.setDecimalSeparator('.');
		DecimalFormat format = new DecimalFormat("#.#", formatsymbols);

//		if (bytes >= TERRA * 1000) {
//			return (float) bytes / TERRA + "TB";
//		} else
		if (bytes >= TERRA) {
			return format.format((float) bytes / TERRA) + "TB";

//		} else if (bytes >= GIGA * 1000) {
//			return (float) bytes / GIGA + "GB";
		} else if (bytes >= GIGA) {
			return format.format((float) bytes / GIGA) + "GB";

//		} else if (bytes >= MEGA * 1000) {
//			return (float) bytes / MEGA + "MB";
		} else if (bytes >= MEGA) {
			return format.format((float) bytes / MEGA) + "MB";

//		} else if (bytes >= KILO * 1000) {
//			return (float) bytes / KILO + "KB";
		} else if (bytes >= KILO) {
			return format.format((float) bytes / KILO) + "KB";
		}
		return Long.toString(bytes) + "B";
	}

	//	public static void main(String args[]) {
	//		System.out.println(formatBytes(1543543));
	//	}

	/**
	 * Make sure argument is in upper case
	 */
	public static long parseBytes(String str) throws NumberFormatException {
		if (str.endsWith("B"))
			str = str.substring(0, str.length() - 1);
		if (str.endsWith("K"))
			return (long)(Double.parseDouble(str.substring(0, str.length() - 1)) * KILO);

		if (str.endsWith("M"))
			return (long)(Double.parseDouble(str.substring(0, str.length() - 1)) * MEGA);

		if (str.endsWith("G"))
			return (long)(Double.parseDouble(str.substring(0, str.length() - 1)) * GIGA);

		if (str.endsWith("T"))
			return (long)(Double.parseDouble(str.substring(0, str.length() - 1)) * TERRA);

		return Long.parseLong(str);
	}
}
