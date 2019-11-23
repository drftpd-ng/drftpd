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
package org.drftpd.commands.nuke;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.commands.nuke.metadata.NukeData;
import org.drftpd.commands.nuke.metadata.NukeUserData;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

/**
 * Some nuke misc methods.
 * @author fr0w
 * @author scitz0
 * @version $Id$
 */
public class NukeUtils {
	private static final Logger logger = LogManager.getLogger(NukeUtils.class);

	private static final Object _lock = new Object();

	public static final String _nukePrefix = "[NUKED]-";

	/**
	 * Calculates the amount of nuked bytes according to the ratio of the user,
	 * size of the release and the multiplier. The formula is this: size * ratio +
	 * size * (multiplier - 1) - size * ratio = will remove the credits the user
	 * won. - size * (multiplier - 1) = that's the penaltie.
	 * 
	 * @param size
	 * @param ratio
	 * @param multiplier
	 * @return the amount of nuked bytes.
	 */
	public static long calculateNukedAmount(long size, float ratio,
                                                int multiplier) {
            if (size != 0 && ratio != 0 && multiplier != 0) {
                return (long) ((size * ratio) + (size * (multiplier - 1)));
            } 
            /* If size is 0 then there are no nuked bytes.  If ratio is 0
             * then this user should not lose credits because they do not
             * earn credits by uploading files.  If multiplier is 0 then
             * this user is exempt from losing credits due to this nuke.
             */

            return 0L;
	}

	public static void getNukeUsers(DirectoryHandle nukeDir,
			Hashtable<String, Long> nukees) throws FileNotFoundException {
		for (InodeHandle inode : nukeDir.getInodeHandlesUnchecked()) {

			try {
				if (inode.isDirectory()) {
					getNukeUsers((DirectoryHandle) inode, nukees);
				}
			} catch (FileNotFoundException e) {
				continue;
			}

			try {
				if (inode.isFile()) {
					String owner = inode.getUsername();
					Long total = nukees.get(owner);

					if (total == null) {
						total = 0L;
					}

					total = total + inode.getSize();
					nukees.put(owner, total);
				}
			} catch (FileNotFoundException e) {
				// Continue
			}
		}
	}

	public static ArrayList<DirectoryHandle> findNukeDirs(DirectoryHandle currentDir, User user, String name) throws FileNotFoundException {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		AdvancedSearchParams params = new AdvancedSearchParams();

		params.setExact(name);
		params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		params.setSortField("lastmodified");
		params.setSortOrder(true);

		try {
			inodes = ie.advancedFind(currentDir, params);
		} catch (IndexException e) {
			throw new FileNotFoundException("Index Exception: "+e.getMessage());
		}

		ArrayList<DirectoryHandle> dirsToNuke = new ArrayList<>();

		for (Map.Entry<String,String> item : inodes.entrySet()) {
			try {
				DirectoryHandle inode = new DirectoryHandle(VirtualFileSystem.fixPath(item.getKey()));
				if (!inode.isHidden(user) && inode.getName().equals(name)) {
					dirsToNuke.add(inode);
				}
			} catch (FileNotFoundException e) {
				// This is ok, could be multiple nukes fired and
				// that is has not yet been reflected in index due to async event.
			}
		}

		return dirsToNuke;
	}

	/*
	 * Method that stripes nuke prefix from dirs path
	 */
	public static String getPathWithoutNukePrefix(String path) {
		String nukeDir = VirtualFileSystem.getLast(path);
		if (!nukeDir.startsWith(_nukePrefix)) {
			// No nuke prefix, just return path;
			return path;
		}
		// Get path for dir without nuke prefix
		String unnukedPath = VirtualFileSystem.stripLast(path);
		if (!unnukedPath.equals(VirtualFileSystem.separator)) {
			unnukedPath += VirtualFileSystem.separator;
		}
		return unnukedPath + nukeDir.substring(_nukePrefix.length());
	}

	/*
	 * Method that adds nuke prefix to path
	 */
	public static String getPathWithNukePrefix(String path) {
		String nukeDir = VirtualFileSystem.getLast(path);
		if (nukeDir.startsWith(_nukePrefix)) {
			// nuke prefix already added, just return path;
			return path;
		}
		// Get path for dir with nuke prefix
		String nukedPath = VirtualFileSystem.stripLast(path);
		if (!nukedPath.equals(VirtualFileSystem.separator)) {
			nukedPath += VirtualFileSystem.separator;
		}
		return nukedPath + _nukePrefix + nukeDir;
	}

	/*
	 * Core functionality to nuke a directory.
	 * Adds NukeData to nukelog and as directory metadata.
	 * Remove credits from users according to multiplier
	 */
	public static NukeData nuke(DirectoryHandle nukeDir, int multiplier, String reason, User user)
			throws NukeException {
		synchronized (_lock) {
			//Start with checking if this dir is already nuked
			NukeData end = NukeBeans.getNukeBeans().findPath(getPathWithoutNukePrefix(nukeDir.getPath()));
			if (end != null) {
				throw new NukeException(end.getPath() + " already nuked for '" + end.getReason() + "'");
			}

			// aborting transfers on the nuked dir.
			GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

			//get nukees with string as key
			Hashtable<String, Long> nukees = new Hashtable<>();

			try {
				getNukeUsers(nukeDir, nukees);
			} catch (FileNotFoundException e) {
				// how come this happened? the dir was just there!
				throw new NukeException("Nuke failed, dir gone!", e);
			}

			// Converting the String Map to a User Map.
			HashMap<User, Long> nukees2 = new HashMap<>(nukees.size());

			for (Map.Entry<String, Long> entry : nukees.entrySet()) {
				String username = entry.getKey();
				User nukee;

				try {
					nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(username);
				} catch (NoSuchUserException e) {
                    logger.warn("Cannot remove credits from {}: {}", username, e.getMessage(), e);
					nukee = null;
				} catch (UserFileException e) {
					throw new NukeException("Cannot read user data for " + username +
							": " + e.getMessage(), e);
				}

				// nukees contains credits as value
				if (nukee == null) {
					Long add = nukees2.get(null);

					if (add == null) {
						add = 0L;
					}

					nukees2.put(null, add + entry.getValue());
				} else {
					nukees2.put(nukee, entry.getValue());
				}
			}

			long nukeDirSize = 0;
			long nukedAmount = 0;

			//update credits, nukedbytes, timesNuked, lastNuked
			for (Map.Entry<User, Long> entry : nukees2.entrySet()) {
				User nukee = entry.getKey();

				long size = entry.getValue();
				nukeDirSize += size;

				if (nukee == null) {
					continue;
				}

				long debt = NukeUtils.calculateNukedAmount(size,
						nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), multiplier);

				nukedAmount += debt;

				nukee.updateCredits(-debt);
				nukee.updateUploadedBytes(-size);

				nukee.getKeyedMap().incrementLong(NukeUserData.NUKEDBYTES, debt);

				nukee.getKeyedMap().incrementInt(NukeUserData.NUKED);
				nukee.getKeyedMap().setObject(NukeUserData.LASTNUKED, System.currentTimeMillis());

				nukee.commit();
			}

			//rename
			String toDirPath = nukeDir.getParent().getPath();
			String toName = _nukePrefix + nukeDir.getName();
			String toFullPath = toDirPath + "/" + toName;

			// Save path before rename to add to nukelog
			String nukeDirPath = nukeDir.getPath();

			try {
				DirectoryHandle root = GlobalContext.getGlobalContext().getRoot();
				// Rename
				nukeDir.renameToUnchecked(root.getNonExistentDirectoryHandle(toFullPath));
				// Updating reference.
				nukeDir = root.getDirectoryUnchecked(toFullPath);
			} catch (IOException ex) {
				logger.warn(ex, ex);
				throw new NukeException("Could not rename to '" + toFullPath + "': " + ex.getMessage(), ex);
			} catch (ObjectNotValidException e) {
				throw new NukeException(toFullPath + " is not a directory");
			}

			NukeData nd = new NukeData();
			nd.setUser(user.getName());
			nd.setPath(nukeDirPath);
			nd.setReason(reason);
			nd.setNukees(nukees);
			nd.setMultiplier(multiplier);
			nd.setAmount(nukedAmount);
			nd.setSize(nukeDirSize);
			nd.setTime(System.currentTimeMillis());

			// adding to the nukelog.
			NukeBeans.getNukeBeans().add(nd);

			// adding nuke metadata to dir.
			try {
				nukeDir.addPluginMetaData(NukeData.NUKEDATA, nd);
			} catch (FileNotFoundException e) {
                logger.warn("Failed to add nuke metadata, dir gone: {}", nukeDir.getPath(), e);
			}

			return nd;
		}
	}


	/*
	 * Core functionality to unnuke a directory.
	 * Removes NukeData from nukelog and as directory metadata.
	 * Readds removed credits from previous nuke.
	 */
	public static NukeData unnuke(DirectoryHandle nukeDir, String reason)
			throws NukeException {
		synchronized (_lock) {
			NukeData nd;

			String unnukedPath = getPathWithoutNukePrefix(nukeDir.getPath());

			try {
				nd = nukeDir.getPluginMetaData(NukeData.NUKEDATA);
			} catch (KeyNotFoundException ex) {
				// Try to get NukeData from nukelog instead
				try {
					nd = NukeBeans.getNukeBeans().get(unnukedPath);
				} catch (ObjectNotFoundException e) {
					throw new NukeException("Unable to unnuke, dir is not nuked.");
				}
			} catch (FileNotFoundException ex) {
				throw new NukeException("Could not find directory: " + nukeDir.getPath());
			}

			GlobalContext.getGlobalContext().getSlaveManager().cancelTransfersInDirectory(nukeDir);

			try {
				DirectoryHandle root = GlobalContext.getGlobalContext().getRoot();
				// Rename
				nukeDir.renameToUnchecked(root.getNonExistentDirectoryHandle(unnukedPath));
				// Updating reference.
				nukeDir = root.getDirectoryUnchecked(unnukedPath);
			} catch (FileExistsException e) {
				throw new NukeException("Error renaming nuke, target dir already exist");
			} catch (FileNotFoundException e) {
				throw new NukeException("Could not find directory: " + nukeDir.getPath());
			} catch (ObjectNotValidException e) {
				throw new NukeException(unnukedPath + " is not a directory");
			}

			for (NukedUser nukeeObj : NukeBeans.getNukeeList(nd)) {
				String nukeeName = nukeeObj.getUsername();
				User nukee;

				try {
					nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeName);
				} catch (NoSuchUserException e) {
					continue;
				} catch (UserFileException e) {
					logger.fatal("error reading userfile", e);
					continue;
				}

				long nukedAmount = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
						nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO),
						nd.getMultiplier());

				nukee.updateCredits(nukedAmount);
				nukee.updateUploadedBytes(nukeeObj.getAmount());

				nukee.getKeyedMap().incrementInt(NukeUserData.NUKED, -1);
				nukee.getKeyedMap().incrementLong(NukeUserData.NUKEDBYTES, -nukedAmount);

				nukee.commit();
			}

			try {
				NukeBeans.getNukeBeans().remove(unnukedPath);
			} catch (ObjectNotFoundException e) {
				logger.warn("Error removing nukelog entry, unnuking anyway.");
			}

			try {
				nukeDir.removePluginMetaData(NukeData.NUKEDATA);
			} catch (FileNotFoundException e) {
                logger.error("Failed to remove nuke metadata from '{}', dir does not exist anymore", nukeDir.getPath(), e);
			}

			nd.setReason(reason);
			nd.setTime(System.currentTimeMillis());

			return nd;
		}
	}
}
