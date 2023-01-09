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
import org.drftpd.master.event.UserEvent;
import org.drftpd.master.permissions.stats.CreditLimitPathPermission;
import org.drftpd.master.permissions.stats.RatioPathPermission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.statistics.master.metadata.StatsUserData;

import java.util.ArrayList;
import java.util.Date;

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

    public float getCreditLossRatio(DirectoryHandle dir, User user) {
        float defaultRatio = (user.getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0) ? 0 : 1;

        return getRatioPathPerm(CREDITLOSS, dir, user, defaultRatio);
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
