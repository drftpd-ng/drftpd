/*
 * Created on 2003-okt-24
 *
 * To change the template for this generated file go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
package net.sf.drftpd.util;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
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
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class ListUtils {

	private static Logger logger = Logger.getLogger(ListUtils.class);

	private ListUtils() {
	}

	public static List list(
		LinkedRemoteFile directoryFile,
		BaseFtpConnection conn) {
		return list(directoryFile, conn, null);
	}

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
					"[" + (good * 100) / sfvfile.size() + "% complete]";
				//				directoryFile,
				listFiles.add(
					new StaticRemoteFile(
						null,
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
}
