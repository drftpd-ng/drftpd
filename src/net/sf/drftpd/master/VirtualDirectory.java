package net.sf.drftpd.master;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
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

	private final static String NEWLINE = "\r\n";
	private final static String DELIM = " ";

	/**
	 * Print file list. Detail listing.
	 * <pre>
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printList(Collection files, Writer out)
		throws IOException {
		//out = new BufferedWriter(out);
		out.write("total 0" + NEWLINE);

		// print file list
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			RemoteFileInterface file = (RemoteFileInterface) iter.next();
			printLine(file, out);
			out.flush();
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
				printLine(file, out);
			} else {
				out.write(file.getName() + NEWLINE);
			}
		}
	}

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

	private static String padToLength(String value, int length) {
		if(value.length() >= length) return value;
		String padding = "          ";
		assert padding.length() > length : "padding must be longer than length";
		return padding.substring(0, length-value.length())+value;
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
		//		if (fl.isDirectory()) {
		//			sb.append('d');
		//		} else {
		//			sb.append('-');
		//		}

		//		if (fl.canRead()) {
		sb.append('r');
		//		} else {
		//			sb.append('-');
		//		}

		//		if (fl.canWrite()) {
		sb.append('w');
		//		} else {
		//			sb.append('-');
		//		}

		if (fl.isDirectory()) {
			sb.append("x");
		} else {
			sb.append("-");
		}
		sb.append("------");
		return sb.toString();
	}

	public static boolean isLegalFileName(String fileName) {
		assert fileName != null;
		return !(fileName.indexOf("/") != -1)
			&& !fileName.equals(".")
			&& !fileName.equals("..");
	}
	/**
	 * Get each directory line.
	 */
	public static void printLine(RemoteFileInterface fl, Writer out)
		throws IOException {
		if (fl.isDirectory()) {
			if (fl instanceof LinkedRemoteFile) {
				LinkedRemoteFile file = (LinkedRemoteFile) fl;
				int filesleft = file.lookupSFVFile().filesLeft();
				if (filesleft != 0)
					out.write(
					"l--------- 3 "
						+ padToLength(fl.getUsername(), 8)
						+ DELIM
						+ padToLength(fl.getGroupname(), 8)
						+ " 0 "
						+ DateUtils.getUnixDate(fl.lastModified())
						+ DELIM
						+ fl.getName()
						+ "-MISSING-"
						+ filesleft
						+ "-FILES"+NEWLINE);
			}
		}
		StringBuffer line = new StringBuffer();
		if (fl instanceof LinkedRemoteFile
			&& !((LinkedRemoteFile) fl).isAvailable()) {
			line.append("---------");
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
		line.append(DateUtils.getUnixDate(fl.lastModified()));
		line.append(DELIM);
		if (fl instanceof LinkedRemoteFile
			&& !((LinkedRemoteFile) fl).isAvailable()) {
			line.append(fl.getName() + "-OFFLINE");
		} else {
			line.append(fl.getName());
		}
		line.append(NEWLINE);
		out.write(line.toString());
	}

}
