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
 * @version $Id: ListUtils.java,v 1.11 2004/01/05 02:20:08 mog Exp $
 */
public class ListUtils {

	private static Logger logger = Logger.getLogger(ListUtils.class);

	public static String PADDING = "          ";

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

		ArrayList listFiles = new ArrayList(dir.getFiles());
		for (Iterator iter = listFiles.iterator(); iter.hasNext();) {
			LinkedRemoteFile element = (LinkedRemoteFile) iter.next();
			if (conn.getConfig() != null
				&& !conn.getConfig().checkPrivPath(conn.getUserNull(), element))
				iter.remove();
		}
		try {
			SFVFile sfvfile = dir.lookupSFVFile();
			SFVStatus sfvstatus = sfvfile.getStatus();

			if (sfvfile.size() != 0) {
				String statusDirName =
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

				listFiles.add(
					new StaticRemoteFile(
						null,
						statusDirName,
						"drftpd",
						"drftpd",
						0L,
						dir.lastModified()));
				for (Iterator iter = sfvfile.getNames().iterator();
					iter.hasNext();
					) {
					String filename = (String) iter.next();
					if (!dir.hasFile(filename)) {
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
				
				for (Iterator iter = dir.getFiles().iterator(); iter.hasNext();) {
					LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
					if (file.isDirectory()) {
						try {
							int filesleft = file.lookupSFVFile().getStatus().getMissing();
							if (filesleft != 0)
							listFiles.add(new StaticRemoteFile(null, file.getName()+"-"+filesleft+"-FILES-MISSING", "drftpd", "drftpd", 0L, dir.lastModified()));
						} catch (IOException e) {
						} // errors getting SFV? FINE! We don't care!
					}

						if (!file.isAvailable())
					listFiles.add(new StaticRemoteFile(Collections.EMPTY_LIST, file.getName()+"-OFFLINE", file.getUsername(), file.getGroupname(), 0L, file.lastModified()));
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
