package net.sf.drftpd.master;

import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
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
		out = new BufferedWriter(out, 65536);
		out.write("total 0"+NEWLINE);

		// print file list
		int i=0;
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			if(++i > 565) return;
			RemoteFileInterface file = (RemoteFileInterface) iter.next();
			printLine(file, out);
			if(i % 100 == 0) {
				System.err.println("i: "+i+" flushing");
				out.flush();
			} 
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

		out.write("total 0"+NEWLINE);
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
		StringBuffer line = new StringBuffer();
		if (fl instanceof LinkedRemoteFile
			&& !((LinkedRemoteFile) fl).isAvailable()) {
			line.append("------");
		} else {
			line.append(getPermission(fl));
		}
		line.append(DELIM);
		line.append((fl.isDirectory() ? "3" : "1"));
		line.append(DELIM);
		line.append(fl.getUsername());
		line.append(DELIM);
		line.append(fl.getGroupname());
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
		try {
			Thread.sleep(10);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

}
