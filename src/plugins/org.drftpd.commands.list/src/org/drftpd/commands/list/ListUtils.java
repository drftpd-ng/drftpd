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
package org.drftpd.commands.list;

import java.io.IOException;
import java.util.ArrayList;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandleInterface;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.InodeHandleInterface;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author mog
 * @version $Id$
 */
public class ListUtils {
	private static final Logger logger = Logger.getLogger(ListUtils.class);
	public static final String PADDING = "          ";

	public static ListElementsContainer list(DirectoryHandle directoryFile, ListElementsContainer container) {
		try {
			return list(directoryFile, container, null);
		} catch (IOException e) {
			logger.info("IOException while listing directory "+directoryFile.getPath(),e);
			return container;
		}
	}

	public static ListElementsContainer list(DirectoryHandle dir, ListElementsContainer container, Object diff) throws IOException {
		ArrayList<InodeHandle> tempFileList = new ArrayList<InodeHandle>(dir.getInodeHandles());
		ArrayList<InodeHandleInterface> listFiles = container.getElements();
		ArrayList<String> fileTypes = container.getFileTypes();
		int numOnline = container.getNumOnline();
		int numTotal = container.getNumTotal();
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = ListUtils.class.getName()+".";
		//boolean id3found = false;
		//ID3Tag mp3tag = null;

		for (InodeHandle element : tempFileList) {

			/* TODO redo this with new permssions somehow
			 * 
			 */
			if ((GlobalContext.getConfig() != null) &&
					!GlobalContext.getConfig().checkPathPermission("privpath",
							container.getSession().getUserNull(container.getUser()), dir, true)) {
				// don't add it
				continue;
			}

			/*            if (element.isFile() &&
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
	            }*/

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
			boolean offlineFilesEnabled = GlobalContext.getConfig().getMainProperties().getProperty("files.offline.enabled", "true").equals("true");
			
			if (offlineFilesEnabled && element.isFile()) {
				if (!((FileHandleInterface) element).isAvailable()) {
					ReplacerEnvironment env = new ReplacerEnvironment();
					env.add("ofilename", element.getName());
					String oFileName = container.getSession().jprintf(bundle, keyPrefix+"files.offline.filename", env, container.getUser());

					listFiles.add(new LightRemoteInode(oFileName, element.getUsername(), element.getGroup(), element.lastModified(), element.getSize()));
					numTotal++;
				}
				// -OFFLINE and "ONLINE" files will both be present until someone implements
				// a way to reupload OFFLINE files.
				// It could be confusing to the user and/or client if the file doesn't exist, but you can't upload it. 
			}

			if (element.isFile()) {
				//else element is a file, and is online
				int typePosition = element.getName().lastIndexOf(".");
				String fileType;
				if (typePosition != -1) {
					fileType = element.getName().substring(typePosition, element.getName().length());
					if (!fileTypes.contains(fileType)) {
						fileTypes.add(fileType);
					}
				}
			}
			numOnline++;
			numTotal++;
			listFiles.add(element);
		}

		/*        
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

	        
		 */
		container.setNumOnline(numOnline);
		container.setNumTotal(numTotal);
		return container;
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
