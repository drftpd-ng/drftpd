package org.drftpd.nuke;

import java.util.Hashtable;
import java.util.Iterator;

import org.drftpd.remotefile.LinkedRemoteFileInterface;

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

    public static void nukeRemoveCredits(LinkedRemoteFileInterface nukeDir,
        Hashtable<String,Long> nukees) {
        for (Iterator iter = nukeDir.getFiles().iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            if (file.isDirectory()) {
                nukeRemoveCredits(file, nukees);
            }

            if (file.isFile()) {
                String owner = file.getUsername();
                Long total = (Long) nukees.get(owner);

                if (total == null) {
                    total = new Long(0);
                }

                total = new Long(total.longValue() + file.length());
                nukees.put(owner, total);
            }
        }
    }
}
