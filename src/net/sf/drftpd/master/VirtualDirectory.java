package net.sf.drftpd.master;

import java.io.IOException;
import java.io.Writer;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.StringTokenizer;

import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.RemoteFile;

/**
 * This class is responsible to handle all virtual directory activities.
 *
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya</a>
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 */
public class VirtualDirectory {

	private final static String NEWLINE = "\r\n";
	private final static String DELIM = " ";

	private String mstRoot = "/";
	private String mstCurrDir = "/";

	private LinkedRemoteFile root;
	private LinkedRemoteFile currentDirectory;
	/**
	 * Default constructor does nothing
	 */
	public VirtualDirectory(LinkedRemoteFile root) {
		this.root = root;
	}


	
//	/**
//	 * Check read permission.
//	 * @deprecated Unusable, user information must be supplied
//	 */
//	public boolean hasReadPermission(LinkedRemoteFile fileName) {
//		return true;
//	}
//
//	/**
//	 * Check file write/delete permission.
//	 * @deprecated Unusable, user information must be supplied
//	 */
//	public boolean hasWritePermission(LinkedRemoteFile fileName) {
//		return true;
//	}
//
//	/**
//	 * Check file create permission.
//	 * @deprecated use RemoteFile methods instead.
//	 */
//	public boolean hasCreatePermission(LinkedRemoteFile fileName) {
//		return true;
//	}

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
			RemoteFile file = (RemoteFile) iter.next();
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
	 * Get file owner.
	 */
	private static String getOwner(RemoteFile fl) {
		return fl.getOwnerUsername();
	}

	/**
	 * Get group name
	 */
	private static String getGroup(RemoteFile fl) {
		return fl.getGroup();
	}

	/**
	 * Get link count
	 */
	private static String getLinkCount(RemoteFile fl) {
		if (fl.isDirectory()) {
			return String.valueOf(3);
		} else {
			return String.valueOf(1);
		}
	}

	/**
	 * Get size
	 */
	private static String getLength(RemoteFile fl) {
		String initStr = "            ";
		long sz = 0;
		if (fl.isFile()) {
			sz = fl.length();
		}
		String szStr = String.valueOf(sz);
		if (szStr.length() > initStr.length()) {
			return szStr;
		}
		return initStr.substring(0, initStr.length() - szStr.length()) + szStr;
	}

	/**
	 * Get last modified date string.
	 */
	private static String getLastModified(RemoteFile fl) {
		long modTime = fl.lastModified();
		Date date = new Date(modTime);
		return DateUtils.getUnixDate(date);
	}

	/**
	 * Get file name.
	 */
	private static String getName(RemoteFile fl) {
		String flName = fl.getName();
		//flName = normalizeSeparateChar(flName);

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
	private static String getPermission(RemoteFile fl) {

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

	/**
	 * Normalize separate characher. Separate character should be '/' always.
	 */
	//	private static String normalizeSeparateChar(String pathName) {
	//		pathName = pathName.replace(File.separatorChar, '/');
	//		pathName = pathName.replace('\\', '/');
	//		return pathName;
	//	}

	/**
	 * Replace dots.
	 * @param inArg the virtaul name
	 * @deprecated VirtualDirectory instance methods are deprecated
	 */
	private String replaceDots(String inArg) {

		// get the starting directory
		String resArg;
		if (inArg.charAt(0) != '/') {
			resArg = mstRoot + mstCurrDir.substring(1);
		} else {
			resArg = mstRoot;
		}

		// strip last '/'
		if (resArg.charAt(resArg.length() - 1) == '/') {
			resArg = resArg.substring(0, resArg.length() - 1);
		}

		// replace ., ~ and ..        
		StringTokenizer st = new StringTokenizer(inArg, "/");
		while (st.hasMoreTokens()) {

			String tok = st.nextToken().trim();

			// . => current directory
			if (tok.equals(".")) {
				continue;
			}

			// .. => parent directory (if not root)
			if (tok.equals("..")) {
				if (resArg.startsWith(mstRoot)) {
					int slashIndex = resArg.lastIndexOf('/');
					if (slashIndex != -1) {
						resArg = resArg.substring(0, slashIndex);
					}
				}
				continue;
			}

			// ~ => home directory (in this case /)
			if (tok.equals("~")) {
				resArg = mstRoot.substring(0, mstRoot.length() - 1).trim();
				continue;
			}

			resArg = resArg + '/' + tok;
		}

		// add last slash if necessary
		if (!inArg.equals("") && (inArg.charAt(inArg.length() - 1) == '/')) {
			resArg = resArg + '/';
		}

		// final check
		if (resArg.length() < mstRoot.length()) {
			resArg = mstRoot;
		}

		return resArg;
	}

	public static boolean isLegalFileName(String fileName) {
		return !(fileName.indexOf("/") != -1) && !fileName.equals(".") && !fileName.equals("..");
	}
	/**
	 * Get each directory line.
	 */
	public static void printLine(RemoteFile fl, Writer out) throws IOException {
		out.write(getPermission(fl));
		out.write(DELIM);
		out.write(DELIM);
		out.write(DELIM);
		out.write(getLinkCount(fl));
		out.write(DELIM);
		out.write(getOwner(fl));
		out.write(DELIM);
		out.write(getGroup(fl));
		out.write(DELIM);
		out.write(getLength(fl));
		out.write(DELIM);
		out.write(getLastModified(fl));
		out.write(DELIM);
		out.write(getName(fl));
		out.write(NEWLINE);
	}

	/**
	 * Get each directory line.
	 */
	/*
	public void printLine(File fl, Writer out) throws IOException {
	    out.write(getPermission(fl));
	    out.write(DELIM);
	    out.write(DELIM);
	    out.write(DELIM);
	    out.write(getLinkCount(fl));
	    out.write(DELIM);
	    out.write(getOwner(fl));
	    out.write(DELIM);
	    out.write(getGroup(fl));
	    out.write(DELIM);
	    out.write(getLength(fl));
	    out.write(DELIM);
	    out.write(getLastModified(fl));
	    out.write(DELIM);
	    out.write(getName(fl));
	out.write(NEWLINE);
	}
	*/

	/**
	 * If the string is not '/', remove last slash.
	 * @deprecated VirtualDirectory instance methods are deprecated
	 */
	private String removeLastSlash(String str) {
		if ((str.length() > 1) && (str.charAt(str.length() - 1) == '/')) {
			str = str.substring(0, str.length() - 1);
		}
		return str;
	}

}
