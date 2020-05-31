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
package org.drftpd.master.commands.usermanagement.expireduser;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.usermanager.GroupFileException;
import org.drftpd.master.usermanager.NoSuchGroupException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserResetHookInterface;

import java.util.Date;
import java.util.Properties;

/**
 * @author cyber
 */
public class ExpiredUserManager implements UserResetHookInterface {

    private static final Logger logger = LogManager.getLogger(ExpiredUserManager.class);

    private boolean _delete;
    private boolean _purge;
    private String _chgrp;
    private String _setgrp;

    public void init() {
        logger.debug("Loaded ExpiredUser Plugin");
        loadConf();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private void loadConf() {
        Properties cfg = ConfigLoader.loadConfig("expireduser.conf");
        _delete = cfg.getProperty("delete", "false").equalsIgnoreCase("true");
        _purge = cfg.getProperty("purge", "false").equalsIgnoreCase("true");
        _chgrp = cfg.getProperty("chgrp", "");
        _setgrp = cfg.getProperty("setgrp", "");
    }

    private void doExpired(User user) {
        if (_delete) {
            user.setDeleted(true);
        }

        if (_purge) {
            user.setDeleted(true);
            user.purge();
            user.commit();
            return;
        }

        if (!_chgrp.isEmpty()) {
            String[] groups = _chgrp.split(" ");
            for (String group : groups) {
                try {
                    user.toggleGroup(user.getUserManager().getGroupByName(group));
                } catch (NoSuchGroupException e) {
                    logger.warn("[doExpired] Tried to chgrp to a unknown group: {}", group);
                } catch (GroupFileException e) {
                    logger.warn("[doExpired] There was an error reading the group file for {} while trying to use chgrp", group, e);
                }
            }
        }

        if (!_setgrp.isEmpty()) {
            String[] groups = _setgrp.split(" ");
            // We can only set 1 primary group on a user, log a warning if there is more than one group specified here
            if (groups.length != 1) {
                logger.warn("[doExpired] We can only set one primary group per user, but found more than one, ignoring others");
            }
            try {
                user.setGroup(user.getUserManager().getGroupByName(groups[0]));
            } catch (NoSuchGroupException e) {
                logger.warn("[doExpired] Tried to setgrp to a unknown group: {}", groups[0]);
            } catch (GroupFileException e) {
                logger.warn("[doExpired] There was an error reading the group file for {} while trying to use chgrp", groups[0], e);
            }
        }
        user.commit();
    }


    public void resetDay(Date d) {
        for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
            if (user.getKeyedMap().getObject(ExpiredUserData.EXPIRES, new Date(4102441199000L)).before(new Date())) {
                doExpired(user);
            }
        }
    }

    public void resetWeek(Date d) {
        // Not Needed - Ignore
    }

    public void resetMonth(Date d) {
        // Not Needed - Reset Day
        resetDay(d);
    }

    public void resetYear(Date d) {
        // Not Needed - Reset Day
        resetDay(d);
    }

    public void resetHour(Date d) {
        // Not Needed - Ignore
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        loadConf();
    }
}
