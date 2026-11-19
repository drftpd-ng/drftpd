/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.statistics.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.event.UploadRevokedEvent;
import org.drftpd.master.event.UserEvent;
import org.drftpd.master.permissions.stats.CreditLimitPathPermission;
import org.drftpd.master.permissions.stats.RatioPathPermission;
import org.drftpd.master.stats.ExtendedTimedStats;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.statistics.master.metadata.StatsUserData;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

/**
 * StatsManager is a class that centralizes the update of users stats.
 *
 * @author fr0w
 * @version $Id$
 */
public class StatsManager implements PluginInterface {
    public static final Key<ArrayList<RatioPathPermission>> CREDITCHECK =
            new Key<>(StatsManager.class, "creditcheck");
    public static final Key<ArrayList<RatioPathPermission>> CREDITLOSS =
            new Key<>(StatsManager.class, "creditloss");
    public static final Key<ArrayList<CreditLimitPathPermission>> CREDITLIMIT =
            new Key<>(StatsManager.class, "creditlimit");
    private static final Logger logger = LogManager.getLogger(StatsManager.class);

    /**
     * Credits for transferring <tt>bytes</tt> at the given ratio.
     * <p>
     * Computed in double precision: the previous <tt>(long) ratio * bytes</tt> cast the ratio to a
     * whole number first (1.5 became 1, 0.5 became 0), and multiplying in float loses precision
     * for large files.
     */
    public static long creditsForBytes(float ratio, long bytes) {
        return (long) ((double) ratio * bytes);
    }

    /**
     * The stats periods an upload made at <tt>uploadTime</tt> is still counted in: always
     * {@link ExtendedTimedStats#P_ALL}, plus month, week and day if the upload happened after the
     * start of the current month, week and day. Uploads made before a period started were already
     * removed from that period when it was reset, so deleting them must not subtract from it.
     *
     * @param uploadTime when the file was uploaded (its last-modified time)
     * @param now        the current time, with the first day of the week set as the site uses it
     *                   (Monday when european.cal=true)
     */
    public static List<Integer> getPeriodsCountingUpload(long uploadTime, Calendar now) {
        Calendar dayStart = (Calendar) now.clone();
        dayStart.set(Calendar.HOUR_OF_DAY, 0);
        dayStart.set(Calendar.MINUTE, 0);
        dayStart.set(Calendar.SECOND, 0);
        dayStart.set(Calendar.MILLISECOND, 0);

        Calendar weekStart = (Calendar) dayStart.clone();
        while (weekStart.get(Calendar.DAY_OF_WEEK) != weekStart.getFirstDayOfWeek()) {
            weekStart.add(Calendar.DAY_OF_MONTH, -1);
        }

        Calendar monthStart = (Calendar) dayStart.clone();
        monthStart.set(Calendar.DAY_OF_MONTH, 1);

        List<Integer> periods = new ArrayList<>();
        periods.add(ExtendedTimedStats.P_ALL);
        if (uploadTime >= monthStart.getTimeInMillis()) {
            periods.add(ExtendedTimedStats.P_MONTH);
        }
        if (uploadTime >= weekStart.getTimeInMillis()) {
            periods.add(ExtendedTimedStats.P_WEEK);
        }
        if (uploadTime >= dayStart.getTimeInMillis()) {
            periods.add(ExtendedTimedStats.P_DAY);
        }
        return periods;
    }

    public static StatsManager getStatsManager() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof StatsManager) {
                return (StatsManager) plugin;
            }
        }

        throw new RuntimeException("Stats plugin is not loaded.");
    }

    public void startPlugin() {
        // Subscribe to events
        AnnotationProcessor.process(this);
        logger.debug("Loaded the Stats plugin successfully");
    }

    public void stopPlugin(String reason) {
        AnnotationProcessor.unprocess(this);
        logger.debug("Unloaded the Stats plugin successfully");
    }

    @EventSubscriber
    public void onUserEvent(UserEvent event) {
        if (event.getCommand().equalsIgnoreCase("LOGIN")) {
            User u = event.getUser();
            u.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date(event.getTime()));
            u.getKeyedMap().incrementInt(StatsUserData.LOGINS);
        }
    }

    /**
     * The site removed an upload on the uploader's behalf (e.g. zipscript deleted a file that
     * doesn't match an .sfv uploaded after it): take back the credits and upload stats, the same
     * as when the file is deleted with DELE.
     */
    @EventSubscriber
    public void onUploadRevokedEvent(UploadRevokedEvent event) {
        try {
            revokeUpload(event.getDirectory(), event.getUsername(), event.getSize(), event.getXferTime(),
                    event.getUploadTime());
        } catch (NoSuchUserException e) {
            logger.debug("User {} does not exist, cannot remove credits for {}", event.getUsername(),
                    event.getFileName());
        } catch (UserFileException e) {
            logger.warn("Error updating credits & stats of {} for {}", event.getUsername(), event.getFileName(), e);
        }
    }

    /**
     * Takes back the credits and upload stats a user got for uploading a file that is now removed.
     *
     * @param dir      the directory the file was in
     * @param userName the user who uploaded the file
     * @param fileSize size of the file
     * @param xferTime transfer time of the upload
     * @param uploadTime when the file was uploaded (its last modified time), to only take the upload
     *                   back from the stats periods it was counted in
     */
    public void revokeUpload(DirectoryHandle dir, String userName, long fileSize, long xferTime, long uploadTime)
            throws NoSuchUserException, UserFileException {
        User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(userName);

        // updating credits
        float ratio = getCreditCheckRatio(dir, user);
        long creditsCheck = creditsForBytes(ratio, fileSize);
        user.updateCredits(-creditsCheck);

        // updating stats
        if (!GlobalContext.getConfig().checkPathPermission("nostatsup", user, dir)) {
            // Only remove the upload from the periods it was counted in, and never below zero
            Calendar now = Calendar.getInstance();
            if (GlobalContext.getConfig().getMainProperties().getProperty("european.cal", "false")
                    .equalsIgnoreCase("true")) {
                now.setFirstDayOfWeek(Calendar.MONDAY);
            }
            for (int period : getPeriodsCountingUpload(uploadTime, now)) {
                user.removeUploadForPeriod(period, fileSize, xferTime);
            }
        }

        user.commit();
    }

    public float getCreditLossRatio(DirectoryHandle dir, User user) {
        float userRatio = user.getKeyedMap().getObjectFloat(UserManagement.RATIO);
        return creditLossRatio(userRatio, GlobalContext.getConfig().getKeyedMap().getObject(CREDITLOSS, null), dir, user);
    }

    /**
     * The creditloss ratio for a user: 0 for leech (ratio 0) users, otherwise the first matching
     * creditloss line of perms.conf, or 1 when none matches.
     *
     * @param perms the configured creditloss lines, may be null
     */
    static float creditLossRatio(float userRatio, List<RatioPathPermission> perms, DirectoryHandle dir, User user) {
        if (isLeech(userRatio)) {
            // Leech (ratio 0) users have unlimited credits: a creditloss line in perms.conf must not
            // make them pay for downloads, or every download is denied with "Not enough credits"
            return 0;
        }
        if (perms != null) {
            for (RatioPathPermission perm : perms) {
                if (perm.checkPath(dir) && perm.check(user)) {
                    return perm.getRatio();
                }
            }
        }
        return 1;
    }

    /**
     * Whether a user ratio means "leech": unlimited downloads, never charged credits.
     */
    static boolean isLeech(float userRatio) {
        return userRatio == 0;
    }

    public float getCreditCheckRatio(DirectoryHandle dir, User user) {
        float defaultRatio = user.getKeyedMap().getObjectFloat(UserManagement.RATIO);

        return getRatioPathPerm(CREDITCHECK, dir, user, defaultRatio);
    }

    private float getRatioPathPerm(Key<ArrayList<RatioPathPermission>> key, DirectoryHandle dir, User user, float defaultRatio) {

        ArrayList<RatioPathPermission> list =
                GlobalContext.getConfig().getKeyedMap().getObject(key, null);

        if (list == null) {
            return defaultRatio;
        }

        for (RatioPathPermission perm : list) {
            if (perm.checkPath(dir) && perm.check(user)) {
                return perm.getRatio();
            }
        }

        return defaultRatio;
    }

    public CreditLimitPathPermission creditLimitCheck(DirectoryHandle dir, User user, int direction) {
        ArrayList<CreditLimitPathPermission> list =
                GlobalContext.getConfig().getKeyedMap().getObject(CREDITLIMIT, null);

        if (list == null) {
            return null;
        }

        for (CreditLimitPathPermission perm : list) {
            if (perm.checkPath(dir) && perm.check(user)) {
                if (perm.getDirection() == direction) {
                    long bytes = switch (perm.getPeriod()) {
                        case "ALL" -> direction == StatsHandler.DIRECTION_UP ?
                                user.getUploadedBytes() : user.getDownloadedBytes();
                        case "DAY" -> direction == StatsHandler.DIRECTION_UP ?
                                user.getUploadedBytesDay() : user.getDownloadedBytesDay();
                        case "WEEK" -> direction == StatsHandler.DIRECTION_UP ?
                                user.getUploadedBytesWeek() : user.getDownloadedBytesWeek();
                        case "MONTH" -> direction == StatsHandler.DIRECTION_UP ?
                                user.getUploadedBytesMonth() : user.getDownloadedBytesMonth();
                        default -> 0L;
                    };
                    if (bytes > perm.getBytes()) {
                        return perm;
                    }
                }
            }
        }

        return null;
    }
}
