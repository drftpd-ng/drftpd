package net.sf.drftpd.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SFVFile.SFVStatus;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: ListUtils.java,v 1.15 2004/01/29 22:19:44 zubov Exp $
 */
public class ListUtils {

	private static final Logger logger = Logger.getLogger(ListUtils.class);

	public static final String PADDING = "          ";

	public static boolean isLegalFileName(String fileName) {
		assert fileName != null;
		return fileName.indexOf("/") == -1
			&& fileName.indexOf('*') == -1
			&& !fileName.equals(".")
			&& !fileName.equals("..");
	}

	public static List list(
		LinkedRemoteFile directoryFile,
		BaseFtpConnection conn) {
		return list(directoryFile, conn, null);
	}

	public static List list(
		LinkedRemoteFile dir,
		BaseFtpConnection conn,
		FtpReply response) {
		ArrayList tempFileList = new ArrayList(dir.getFiles());
		ArrayList listFiles = new ArrayList();
		int numOnline = 0;
		int numTotal = 0;
		for (Iterator iter = tempFileList.iterator(); iter.hasNext();) {
			LinkedRemoteFile element = (LinkedRemoteFile) iter.next();
			if (conn.getConfig() != null
				&& !conn.getConfig().checkPrivPath(conn.getUserNull(), element)) {
				// don't add it
				continue;
			}
			if (element.isDirectory()) {
				try {
					int filesleft =
						element.lookupSFVFile().getStatus().getMissing();
					if (filesleft != 0)
						listFiles.add(
							new StaticRemoteFile(
								null,
								element.getName()
									+ "-"
									+ filesleft
									+ "-FILES-MISSING",
								"drftpd",
								"drftpd",
								0L,
								dir.lastModified()));
				} catch (IOException e) {
				} // errors getting SFV? FINE! We don't care!
				// is a directory
				//				numOnline++; // do not want to include directories in the file count
				//				numTotal++;
				listFiles.add(element);
				continue;
			} else if (
				!element.isAvailable()) { // directories are always available
				listFiles.add(
					new StaticRemoteFile(
						Collections.EMPTY_LIST,
						element.getName() + "-OFFLINE",
						element.getUsername(),
						element.getGroupname(),
						0L,
						element.lastModified()));
				numTotal++;
				continue; // don't add it's "ONLINE" counterpart
			}
			//else element is a file, and is online
			numOnline++;
			numTotal++;
			listFiles.add(element);
		}
		String statusDirName = null;
		try {
			SFVFile sfvfile = dir.lookupSFVFile();
			SFVStatus sfvstatus = sfvfile.getStatus();
			if (sfvfile.size() != 0) {
				if (sfvstatus.getPresent() != 0) {
					statusDirName =
						"[ "
							+ sfvstatus.getPresent()
							+ "/"
							+ sfvfile.size()
							+ " = "
							+ (sfvstatus.getPresent() * 100) / sfvfile.size()
							+ "% complete | "
							+ sfvstatus.getAvailable()
							+ "/"
							+ sfvstatus.getPresent()
							+ " = "
							+ (sfvstatus.getAvailable() * 100)
								/ sfvstatus.getPresent()
							+ "% online ]";
				} else {
					statusDirName =
						"[ "
							+ sfvstatus.getPresent()
							+ "/"
							+ sfvfile.size()
							+ " = "
							+ (sfvstatus.getPresent() * 100) / sfvfile.size()
							+ "% complete | 0/0 = 0% online ]";
				}

				for (Iterator iter = sfvfile.getNames().iterator();
					iter.hasNext();
					) {
					String filename = (String) iter.next();
					if (!dir.hasFile(filename)) {
						//listFiles.remove()
						listFiles.add(
							new StaticRemoteFile(
								Collections.EMPTY_LIST,
								filename + "-MISSING",
								"drftpd",
								"drftpd",
								0L,
								dir.lastModified()));
					}
				}
			}
		} catch (NoAvailableSlaveException e) {
			logger.warn("No available slaves for SFV file", e);
		} catch (FileNotFoundException e) {
			// no sfv file in directory - just skip it
		} catch (IOException e) {
			logger.warn("IO error loading SFV file", e);
		} catch (Throwable e) {
			if (response != null)
				response.addComment("zipscript error: " + e.getMessage());
			logger.warn("zipscript error", e);
		}
		if (statusDirName == null && numTotal != 0) {
			statusDirName =
				"[ "
					+ numOnline
					+ "/"
					+ numTotal
					+ " = "
					+ (numOnline * 100) / numTotal
					+ "% online ]";
		}
		if (statusDirName != null)
			listFiles.add(
				new StaticRemoteFile(
					null,
					statusDirName,
					"drftpd",
					"drftpd",
					0L,
					dir.lastModified()));
		return listFiles;
	}

	public static String padToLength(String value, int length) {
		if (value.length() >= length)
			return value;
		assert PADDING.length() > length : "padding must be longer than length";
		return PADDING.substring(0, length - value.length()) + value;
	}

	private ListUtils() {
	}
}
