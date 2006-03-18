package org.drftpd.nuke;

import java.io.FileNotFoundException;
import java.util.Hashtable;
import java.util.Iterator;

import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

public class NukeUtils {
	
	/**
	 * Calculates the amount of nuked bytes according to the
	 * ratio of the user, size of the release and the multiplier.
	 * The formula is this: size * ratio + size * (multiplier - 1)
	 * - size * ratio = will remove the credits the user won.
	 * - size * (multiplier - 1) = that's the penaltie.
	 * @param size
	 * @param ratio
	 * @param multiplier
	 * @return the amount of nuked bytes.
	 */
    public static long calculateNukedAmount(long size, float ratio,
        int multiplier) {
        return (long) ((size * ratio) + (size * (multiplier - 1)));
    }

    public static void nukeRemoveCredits(DirectoryHandle nukeDir,
        Hashtable<String,Long> nukees) throws FileNotFoundException {
        for (Iterator<InodeHandle> iter = nukeDir.getAllHandles().iterator(); iter.hasNext();) {
        	InodeHandle file = iter.next();

            try {
				if (file.isDirectory()) {
				    nukeRemoveCredits((DirectoryHandle) file, nukees);
				}
			} catch (FileNotFoundException e) {
				continue;
			}

            try {
				if (file.isFile()) {
				    String owner = file.getUsername();
				    Long total = (Long) nukees.get(owner);

				    if (total == null) {
				        total = new Long(0);
				    }

				    total = new Long(total.longValue() + file.getSize());
				    nukees.put(owner, total);
				}
			} catch (FileNotFoundException e) {
				continue;
			}
        }
    }
}
