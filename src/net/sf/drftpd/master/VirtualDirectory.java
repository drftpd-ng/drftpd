package net.sf.drftpd.master;

import java.io.IOException;
import java.io.Writer;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.Iterator;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFileInterface;

/**
 * This class is responsible to handle all virtual directory activities.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class VirtualDirectory {
	private final static String DELIM = " ";
	private final static String[] MONTHS =
		{
			"Jan",
			"Feb",
			"Mar",
			"Apr",
			"May",
			"Jun",
			"Jul",
			"Aug",
			"Sep",
			"Oct",
			"Nov",
			"Dec" };

	private final static DateFormat AFTER_SIX = new SimpleDateFormat(" yyyy");
	private final static DateFormat BEFORE_SIX = new SimpleDateFormat("HH:mm");
	private final static DateFormat FULL = new SimpleDateFormat("HH:mm:ss yyyy");

	public static String getUnixDate(long date, boolean fulldate) {
		Date date1 = new Date(date);
		long dateTime = date1.getTime();
		if (dateTime < 0) {
			return "------------";
		}

		Calendar cal = new GregorianCalendar();
		cal.setTime(date1);
		String firstPart = MONTHS[cal.get(Calendar.MONTH)] + ' ';

		String dateStr = String.valueOf(cal.get(Calendar.DATE));
		if (dateStr.length() == 1) {
			dateStr = ' ' + dateStr;
		}
		firstPart += dateStr + ' ';

		long nowTime = System.currentTimeMillis();
		if (fulldate) {
			return firstPart + FULL.format(date1);
		} else if (
			Math.abs(nowTime - dateTime) > 183L * 24L * 60L * 60L * 1000L) {
			return firstPart + AFTER_SIX.format(date1);
		} else {
			return firstPart + BEFORE_SIX.format(date1);
		}
	}

	private final static String NEWLINE = "\r\n";

	/**
	 * Get size
	 * @deprecated
	 */
	private static String getLength(RemoteFileInterface fl) {
		String initStr = "             ";
		String szStr = Long.toString(fl.length());
		if (szStr.length() > initStr.length()) {
			return szStr;
		}
		return initStr.substring(0, initStr.length() - szStr.length()) + szStr;
	}

	/**
	 * Get file name.
	 */
	private static String getName(LinkedRemoteFile fl) {
		String flName = fl.getName();

		int lastIndex = flName.lastIndexOf("/");
		if (lastIndex == -1) {
			return flName;
		} else {
			return flName.substring(lastIndex + 1);
		}
	}

	/**
	 * Get permission string.
	 */
	private static String getPermission(RemoteFileInterface fl) {

		StringBuffer sb = new StringBuffer(13);
		sb.append(fl.isDirectory() ? 'd' : '-');

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		sb.append("rw");
		sb.append(fl.isDirectory() ? "x" : "-");

		return sb.toString();
	}

	public static boolean isLegalFileName(String fileName) {
		assert fileName != null;
		return !(fileName.indexOf("/") != -1)
			&& !fileName.equals(".")
			&& !fileName.equals("..");
	}

	private static String padToLength(String value, int length) {
		if (value.length() >= length)
			return value;
		String padding = "          ";
		assert padding.length() > length : "padding must be longer than length";
		return padding.substring(0, length - value.length()) + value;
	}

	/**
	 * Get each directory line.
	 */
	public static void printLine(RemoteFileInterface fl, Writer out, boolean fulldate)
		throws IOException {
		StringBuffer line = new StringBuffer();
		if (fl instanceof LinkedRemoteFile
			&& !((LinkedRemoteFile) fl).isAvailable()) {
			line.append("----------");
		} else {
			line.append(getPermission(fl));
		}
		line.append(DELIM);
		line.append((fl.isDirectory() ? "3" : "1"));
		line.append(DELIM);
		line.append(padToLength(fl.getUsername(), 8));
		line.append(DELIM);
		line.append(padToLength(fl.getGroupname(), 8));
		line.append(DELIM);
		line.append(getLength(fl));
		line.append(DELIM);
		line.append(getUnixDate(fl.lastModified(), fulldate));
		line.append(DELIM);
		line.append(fl.getName());
		line.append(NEWLINE);
		out.write(line.toString());

		if (fl.isDirectory() && fl instanceof LinkedRemoteFile) {
			LinkedRemoteFile file = (LinkedRemoteFile) fl;
			try {
				int filesleft = file.lookupSFVFile().filesLeft();
				if (filesleft != 0)
					out.write(
						"l--------- 3 "
							+ padToLength(fl.getUsername(), 8)
							+ DELIM
							+ padToLength(fl.getGroupname(), 8)
							+ "             0 "
							+ getUnixDate(fl.lastModified(), fulldate)
							+ DELIM
							+ fl.getName()
							+ "-MISSING-"
							+ filesleft
							+ "-FILES"
							+ NEWLINE);
			} catch (IOException e) {
			} // errors getting SFV? FINE! We don't care!
		}
		if (fl instanceof LinkedRemoteFile) {
			LinkedRemoteFile file = (LinkedRemoteFile) fl;
			if (!file.isAvailable())
				out.write(
					"l--------- 3 "
						+ padToLength(fl.getUsername(), 8)
						+ DELIM
						+ padToLength(fl.getGroupname(), 8)
						+ "             0 "
						+ getUnixDate(fl.lastModified(), fulldate)
						+ DELIM
						+ fl.getName()
						+ "-OFFLINE"
						+ NEWLINE);
		}
	}

	/**
	 * Print file list. Detail listing.
	 * <pre>
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printList(Collection files, Writer os, boolean fulldate)
		throws IOException {
		//out = new BufferedWriter(out);
		os.write("total 0" + NEWLINE);

		// print file list
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			RemoteFileInterface file = (RemoteFileInterface) iter.next();
			printLine(file, os, fulldate);
		}
	}

	/**
	 * Print file list.
	 * <pre>
	 *   -l : detail listing
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printNList(
		Collection fileList,
		boolean bDetail,
		Writer out)
		throws IOException {

		for (Iterator iter = fileList.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (bDetail) {
				printLine(file, out, false);
			} else {
				out.write(file.getName() + NEWLINE);
			}
		}
	}

}
