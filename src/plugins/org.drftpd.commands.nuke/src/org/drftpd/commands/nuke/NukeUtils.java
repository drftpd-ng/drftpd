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

import java.io.FileNotFoundException;
import java.util.Hashtable;

import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * Some nuke misc methods.
 * @author fr0w
 * @version $Id$
 */
public class NukeUtils {

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
            } else {

                /* If size is 0 then there are no nuked bytes.  If ratio is 0
                 * then this user should not lose credits because they do not
                 * earn credits by uploading files.  If multiplier is 0 then
                 * this user is exempt from losing credits due to this nuke.
                 */

                return 0L;
            }
	}

	public static void nukeRemoveCredits(DirectoryHandle nukeDir,
			Hashtable<String, Long> nukees) throws FileNotFoundException {
		for (InodeHandle inode : nukeDir.getInodeHandlesUnchecked()) {

			try {
				if (inode.isDirectory()) {
					nukeRemoveCredits((DirectoryHandle) inode, nukees);
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

					total = total.longValue() + inode.getSize();
					nukees.put(owner, total);
				}
			} catch (FileNotFoundException e) {
				continue;
			}
		}
	}
}
