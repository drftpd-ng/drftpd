/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.util;

import net.sf.drftpd.ID3Tag;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SFVFile.SFVStatus;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.remotefile.StaticRemoteFile;

import org.apache.log4j.Logger;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;


/**
 * @author mog
 * @version $Id: ListUtils.java,v 1.29 2004/09/13 15:04:59 zubov Exp $
 */
public class ListUtils {
    private static final Logger logger = Logger.getLogger(ListUtils.class);
    public static final String PADDING = "          ";

    private ListUtils() {
    }

    public static boolean isLegalFileName(String fileName) {
        if (fileName == null) {
            throw new RuntimeException();
        }

        return (fileName.indexOf("/") == -1) && (fileName.indexOf('*') == -1) &&
        !fileName.equals(".") && !fileName.equals("..");
    }

    public static List list(LinkedRemoteFileInterface directoryFile,
        BaseFtpConnection conn) {
        return list(directoryFile, conn, null);
    }

    public static List list(LinkedRemoteFileInterface dir,
        BaseFtpConnection conn, FtpReply response) {
        ArrayList tempFileList = new ArrayList(dir.getFiles());
        ArrayList listFiles = new ArrayList();
        int numOnline = 0;
        int numTotal = 0;
        boolean id3found = false;
        ID3Tag mp3tag = null;

        for (Iterator iter = tempFileList.iterator(); iter.hasNext();) {
            LinkedRemoteFile element = (LinkedRemoteFile) iter.next();

            if ((conn.getConnectionManager().getGlobalContext().getConfig() != null) &&
                    !conn.getConnectionManager().getGlobalContext().getConfig()
                             .checkPrivPath(conn.getUserNull(), element)) {
                // don't add it
                continue;
            }

            if (element.isFile() &&
                    element.getName().toLowerCase().endsWith(".mp3") &&
                    (id3found == false)) {
                try {
                    mp3tag = element.getID3v1Tag();
                    id3found = true;
                } catch (FileNotFoundException e) {
                    logger.warn("FileNotFoundException: " + element.getPath(), e);
                } catch (IOException e) {
                    logger.warn("IOException: " + element.getPath(), e);
                } catch (NoAvailableSlaveException e) {
                    logger.warn("NoAvailableSlaveException: " +
                        element.getPath(), e);
                }
            }

            //			if (element.isDirectory()) { // can slow listing
            //				try {
            //					int filesleft =
            //						element.lookupSFVFile().getStatus().getMissing();
            //					if (filesleft != 0)
            //						listFiles.add(
            //							new StaticRemoteFile(
            //								null,
            //								element.getName()
            //									+ "-"
            //									+ filesleft
            //									+ "-FILES-MISSING",
            //								"drftpd",
            //								"drftpd",
            //								0L,
            //								dir.lastModified()));
            //				} catch (IOException e) {
            //				} // errors getting SFV? FINE! We don't care!
            //				// is a directory
            //				//				numOnline++; // do not want to include directories in the file count
            //				//				numTotal++;
            //				listFiles.add(element);
            //				continue;
            //			} else if (
            if (!element.isAvailable()) { // directories are always available
                listFiles.add(new StaticRemoteFile(Collections.EMPTY_LIST,
                        element.getName() + "-OFFLINE", element.getUsername(),
                        element.getGroupname(), element.length(),
                        element.lastModified()));
                numTotal++;

                // -OFFLINE and "ONLINE" files will both be present until someoe implements
                // a way to reupload OFFLINE files.
                // It could be confusing to the user and/or client if the file doesn't exist, but you can't upload it. 
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
                statusDirName = "[ ";

                if (sfvstatus.getMissing() != 0) {
                    statusDirName += (sfvstatus.getMissing() +
                    " files missing = ");
                }

                statusDirName += (((sfvstatus.getPresent() == 0) ? "0"
                                                                 : ("" +
                ((sfvstatus.getPresent() * 100) / sfvfile.size()))) +
                "% complete");

                if (sfvstatus.getOffline() != 0) {
                    statusDirName += (" | " + sfvstatus.getOffline() +
                    " files offline = " +
                    ((sfvstatus.getAvailable() * 100) / sfvstatus.getPresent()) +
                    "% online");
                }

                //mp3tag info added by teflon
                if (mp3tag != null) {
                    statusDirName += (" | id3info - " + mp3tag.getGenre() +
                    " " + mp3tag.getYear());
                }

                statusDirName += " ]";

                if (statusDirName == null) {
                    throw new RuntimeException();
                }

                listFiles.add(new StaticRemoteFile(null, statusDirName,
                        "drftpd", "drftpd", 0L, dir.lastModified()));

                for (Iterator iter = sfvfile.getNames().iterator();
                        iter.hasNext();) {
                    String filename = (String) iter.next();

                    if (!dir.hasFile(filename)) {
                        //listFiles.remove()
                        listFiles.add(new StaticRemoteFile(
                                Collections.EMPTY_LIST, filename + "-MISSING",
                                "drftpd", "drftpd", 0L, dir.lastModified()));
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
            logger.warn("zipscript error", e);
        }

        return listFiles;
    }

    public static String padToLength(String value, int length) {
        if (value.length() >= length) {
            return value;
        }

        if (PADDING.length() < length) {
            throw new RuntimeException("padding must be longer than length");
        }

        return PADDING.substring(0, length - value.length()) + value;
    }
}
