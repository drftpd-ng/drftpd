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
	public static void printList(Collection files, Writer out) throws IOException {

		// check pattern
		//directoryName = replaceDots(directoryName);
//		int slashIndex = directoryName.lastIndexOf('/');
//		if ((slashIndex != -1)
//			&& (slashIndex != (directoryName.length() - 1))) {
//			pattern = directoryName.substring(slashIndex + 1);
//			directoryName = directoryName.substring(0, slashIndex + 1);
//		}

		/*
		Map lstDirObj = filesystem;
		StringTokenizer st = new StringTokenizer(lsDirName, "/");
		while(st.hasMoreTokens()) {
		String nextToken = st.nextToken();
		System.out.println(nextToken);
		Object obj = lstDirObj.get(nextToken);
		if(lstDirObj==null) return false;
		if(!(lstDirObj instanceof Map)) return false;
		lstDirObj = (Map)obj;
		}*/

		
		//}

		// print file list
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			RemoteFileInterface file = (RemoteFileInterface) iter.next();
			printLine(file, out);
		}
//		if (fileList != null) {
//			for (int i = 0; i < fileList.length; i++) {
//				printLine(fileList[i], out);
//			}
//		}
	}

	/**
	 * Print file list.
	 * <pre>
	 *   -l : detail listing
	 *   -a : display all (including hidden files)
	 * </pre>
	 * @return true if success
	 */
	public static void printNList(Collection fileList, boolean bDetail, Writer out) throws IOException {


		// check pattern
		//lsDirName = replaceDots(lsDirName);
//		int slashIndex = lsDirName.lastIndexOf('/');
//		if ((slashIndex != -1) && (slashIndex != (lsDirName.length() - 1))) {
//			//pattern = lsDirName.substring(slashIndex + 1);
//			lsDirName = lsDirName.substring(0, slashIndex + 1);
//		}

		// check directory
		//File lstDirObj = new File(lsDirName);
		//LinkedRemoteFile lstDirObj = dir.lookupFile(lsDirName);

		//if (!lstDirObj.isDirectory()) {
		//	return false;
		//}

		// get file list
		///RemoteFile flLst[];
		//if ((pattern == null) || pattern.equals("*") || pattern.equals("")) {
		//	flLst = lstDirObj.listFiles();
		//} else {
		//	flLst = lstDirObj.listFiles(); //new FileRegularFilter(pattern));
		//}

		// print file list
		//if (flLst != null) {
			//for (int i = 0; i < flLst.length; i++) {
			for (Iterator iter = fileList.iterator(); iter.hasNext();) {
				LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
				
		//		if ((!bAll) && flLst[i].isHidden()) {
		//			continue;
		//		}
				if (bDetail) {
					printLine(file, out);
				} else {
					out.write(file.getName() + NEWLINE);
				}
			}
		//}
	}

	/**
	 * Get size
	 * @deprecated
	 */
	private static String getLength(RemoteFileInterface fl) {
		String initStr = "            ";
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
		if (fl.isDirectory()) {
			sb.append('d');
		} else {
			sb.append('-');
		}

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
		return !(fileName.indexOf("/") != -1) && !fileName.equals(".") && !fileName.equals("..");
	}
	/**
	 * Get each directory line.
	 */
	public static void printLine(RemoteFileInterface fl, Writer out) throws IOException {
		if(fl instanceof LinkedRemoteFile && !((LinkedRemoteFile)fl).isAvailable())  {
			out.write("------");
		} else {
			out.write(getPermission(fl));
		}
		out.write(DELIM);
		out.write(DELIM);
		out.write(DELIM);
		out.write((fl.isDirectory() ? "3" : "1"));
		out.write(DELIM);
		out.write(fl.getUsername());
		out.write(DELIM);
		out.write(fl.getGroupname());
		out.write(DELIM);
		out.write(getLength(fl));
		out.write(DELIM);
		out.write(DateUtils.getUnixDate(fl.lastModified()));
		out.write(DELIM);
		//out.write(getName(fl));
		if(fl instanceof LinkedRemoteFile && !((LinkedRemoteFile)fl).isAvailable())  {
			out.write(fl.getName() + "-OFFLINE");
		} else {
			out.write(fl.getName());
		}
		out.write(NEWLINE);
	}

}
