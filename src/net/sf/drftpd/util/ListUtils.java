package net.sf.drftpd.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

/**
 * @author mog
 * @version $Id: ListUtils.java,v 1.3 2003/11/19 00:20:54 mog Exp $
 */
public class ListUtils {

	private static Logger logger = Logger.getLogger(ListUtils.class);

	public static String PADDING = "          ";

	//TODO MOVE ME
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

	//TODO -OFFLINE and -MISSING files
	public static List list(
		LinkedRemoteFile directoryFile,
		BaseFtpConnection conn,
		FtpReply response) {
		ArrayList listFiles = new ArrayList(directoryFile.getFiles());
		for (Iterator iter = listFiles.iterator(); iter.hasNext();) {
			LinkedRemoteFile element = (LinkedRemoteFile) iter.next();
			if (!conn.getConfig().checkPrivPath(conn.getUserNull(), element))
				iter.remove();
		}
		try {
			SFVFile sfvfile = directoryFile.lookupSFVFile();
			int good = sfvfile.finishedFiles();
			if (sfvfile.size() != 0) {
				String statusDirName =
					"[ "
						+ good
						+ "/"
						+ sfvfile.size()
						+ " = "
						+ (good * 100) / sfvfile.size()
						+ "% complete]";
				//				directoryFile,
				listFiles.add(
					new StaticRemoteFile(
						Collections.EMPTY_LIST,
						statusDirName,
						"drftpd",
						"drftpd",
						0L,
						System.currentTimeMillis()));
			}
		} catch (NoAvailableSlaveException e) {
			logger.log(Level.WARN, "No available slaves for SFV file");
		} catch (FileNotFoundException e) {
			// no sfv file in directory - just skip it
		} catch (IOException e) {
			logger.log(Level.WARN, "IO error loading SFV file", e);
		} catch (Throwable e) {
			if (response != null)
				response.addComment("zipscript error: " + e.getMessage());
			logger.log(Level.WARN, "zipscript error", e);
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
