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
package org.drftpd.remotefile;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.SFVFile;
import org.drftpd.SFVFile.SFVStatus;
import org.drftpd.commands.Reply;
import org.drftpd.id3.ID3Tag;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @author mog
 * @version $Id: ListUtils.java 894 2005-01-13 22:32:32Z zubov $
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
        try {
			return list(directoryFile, conn, null);
		} catch (IOException e) {
			logger.info("IOException while listing directory "+directoryFile.getPath(),e);
			return new ArrayList();
		}
    }

    public static List list(LinkedRemoteFileInterface dir,
        BaseFtpConnection conn, Reply response) throws IOException {
        ArrayList tempFileList = new ArrayList<RemoteFileInterface>(dir.getFiles());
        ArrayList<RemoteFileInterface> listFiles = new ArrayList<RemoteFileInterface>();
        int numOnline = 0;
        int numTotal = 0;
        boolean id3found = false;
        ID3Tag mp3tag = null;

        for (Iterator iter = tempFileList.iterator(); iter.hasNext();) {
            LinkedRemoteFile element = (LinkedRemoteFile) iter.next();

            if ((conn.getGlobalContext().getConnectionManager()
                         .getGlobalContext().getConfig() != null) &&
                    !conn.getGlobalContext().getConnectionManager()
					 .getGlobalContext().getConfig().checkPathPermission("privpath", conn.getUserNull(), element, true)) {
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
            if (!element.isAvailable() && 
                    conn.getGlobalContext().getZsConfig().offlineFilesEnabled()) { // directories are always available
                ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("ofilename",element.getName());
				String oFileName = conn.jprintf(ListUtils.class, "files.offline.filename", env);
				listFiles.add(new StaticRemoteFile(Collections.EMPTY_LIST,
				        oFileName, element.getUsername(),
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
			if (DIZPlugin.zipFilesOnline(dir) > 0) {
				DIZFile diz = null;
				diz = new DIZFile(DIZPlugin.getZipFile(dir));
				if (diz.getDiz() != null && diz.getTotal() > 0) {
					ReplacerEnvironment env = new ReplacerEnvironment();

					if ((diz.getTotal() - DIZPlugin.zipFilesPresent(dir)) > 0) {
						env.add("missing.number", ""
								+ (diz.getTotal() - DIZPlugin
										.zipFilesPresent(dir)));
						env.add("missing.percent", ""
								+ (((diz.getTotal() - DIZPlugin
										.zipFilesPresent(dir)) * 100) / diz
										.getTotal()));
						env.add("missing", conn.jprintf(ListUtils.class,
								"statusbar.missing", env));
					} else {
						env.add("missing", "");
					}

					if (DIZPlugin.zipFilesPresent(dir) > 0) {
						env.add("complete.total", "" + diz.getTotal());
						env.add("complete.number", ""
								+ DIZPlugin.zipFilesPresent(dir));
						env.add("complete.percent", ""
								+ ((DIZPlugin.zipFilesPresent(dir) * 100) / diz
										.getTotal()));
						env.add("complete.totalbytes", Bytes
								.formatBytes(DIZPlugin.zipDirSize(diz
										.getParent())));
					} else {
						env.add("complete.number", "0");
						env.add("complete.percent", "0");
						env.add("complete.totalbytes", "0");
					}

					env.add("complete", conn.jprintf(ListUtils.class,
							"statusbar.complete", env));

					if (DIZPlugin.zipFilesOffline(dir) > 0) {
						env.add("offline.number", ""
								+ DIZPlugin.zipFilesOffline(dir));
						env.add("offline.percent", ""
								+ (DIZPlugin.zipFilesOffline(dir) * 100)
								/ DIZPlugin.zipFilesPresent(dir));
						env.add("online.number", ""
								+ DIZPlugin.zipFilesOnline(dir));
						env.add("online.percent", ""
								+ (DIZPlugin.zipFilesOnline(dir) * 100)
								/ DIZPlugin.zipFilesPresent(dir));
						env.add("offline", conn.jprintf(ListUtils.class,
								"statusbar.offline", env));
					} else {
						env.add("offline", "");
					}

					env.add("id3tag", "");

					statusDirName = conn.jprintf(ListUtils.class,
							"statusbar.format", env);

					if (statusDirName == null) {
						throw new RuntimeException();
					}

					if (conn.getGlobalContext().getZsConfig()
							.statusBarEnabled()) {
						listFiles.add(new StaticRemoteFile(null, statusDirName,
								"drftpd", "drftpd", 0L, dir.lastModified()));
					}

					return listFiles;
				}
			}
		} catch (FileNotFoundException e) {
		} catch (NoAvailableSlaveException e) {
		}

        try {
            SFVFile sfvfile = dir.lookupSFVFile();
            SFVStatus sfvstatus = sfvfile.getStatus();
			ReplacerEnvironment env = new ReplacerEnvironment();

            if (sfvfile.size() != 0) {

                if (sfvstatus.getMissing() != 0) {
					env.add("missing.number","" + sfvstatus.getMissing());
					env.add("missing.percent","" + (sfvstatus.getMissing() * 100) / sfvfile.size());
					env.add("missing",conn.jprintf(ListUtils.class, "statusbar.missing",env));
                } else {
                	env.add("missing","");
                }

                env.add("complete.total", "" + sfvfile.size());
                env.add("complete.number", "" + sfvstatus.getPresent());
                env.add("complete.percent", "" + (sfvstatus.getPresent() * 100)
                		/ sfvfile.size());
                env.add("complete.totalbytes", Bytes.formatBytes(sfvfile
                		.getTotalBytes()));
                env.add("complete", conn.jprintf(ListUtils.class,
                		"statusbar.complete", env));

                if (sfvstatus.getOffline() != 0) {
					env.add("offline.number","" + sfvstatus.getOffline());
					env.add("offline.percent",""+ (sfvstatus.getOffline() * 100) / sfvstatus.getPresent());
					env.add("online.number","" + sfvstatus.getPresent());
					env.add("online.percent","" + (sfvstatus.getAvailable() * 100) / sfvstatus.getPresent());
					env.add("offline",conn.jprintf(ListUtils.class, "statusbar.offline",env));
                } else {
                	env.add("offline","");
                }

                //mp3tag info added by teflon artist, album, title, genre, year
                if (id3found) {
                	env.add("artist",mp3tag.getArtist().trim());
                	env.add("album",mp3tag.getAlbum().trim());
                	env.add("title", mp3tag.getTitle().trim());
                	env.add("genre", mp3tag.getGenre().trim());
                	env.add("year", mp3tag.getYear().trim());
                	env.add("id3tag",conn.jprintf(ListUtils.class, "statusbar.id3tag",env));
                } else {
                	env.add("id3tag","");
                }

                statusDirName = conn.jprintf(ListUtils.class, "statusbar.format",env);

                if (statusDirName == null) {
                    throw new RuntimeException();
                }

                if (conn.getGlobalContext().getZsConfig().statusBarEnabled()) {
                	listFiles.add(new StaticRemoteFile(null, statusDirName,
                        "drftpd", "drftpd", 0L, dir.lastModified()));
                }

                if (conn.getGlobalContext().getZsConfig().missingFilesEnabled()) {
                    for (Iterator iter = sfvfile.getNames().iterator();
                    	iter.hasNext();) {
                    	String filename = (String) iter.next();

                    	if (!dir.hasFile(filename) || dir.getXfertime() == -1) {
                    		//listFiles.remove()
                    		env.add("mfilename",filename);
                    		listFiles.add(new StaticRemoteFile(
                    				Collections.EMPTY_LIST, 
									conn.jprintf(ListUtils.class, "files.missing.filename",env),
									"drftpd", "drftpd", 0L, dir.lastModified()));
                    	}
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
