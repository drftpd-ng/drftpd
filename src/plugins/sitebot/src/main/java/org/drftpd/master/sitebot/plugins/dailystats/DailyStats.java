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
package org.drftpd.master.sitebot.plugins.dailystats;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.sitebot.plugins.dailystats.event.StatsEvent;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserResetPreHookInterface;
import org.drftpd.master.usermanager.util.UserComparator;

import java.util.*;

/**
 * @author djb61
 * @author cyber
 * @version $Id: DailyStats.java 2230 2010-10-27 21:37:13Z scitz0 $
 */
public class DailyStats implements UserResetPreHookInterface {

    private static final Logger logger = LogManager.getLogger(DailyStats.class);

    private boolean _showzero = false;
    private int _outputnum;
    private String[] _exempt;

    public void init() {
        logger.info("Starting daily stats plugin");
        loadConf();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private void loadConf() {
        Properties cfg = ConfigLoader.loadPluginConfig("dailystats.conf");
        _showzero = cfg.getProperty("showzero", "false").equalsIgnoreCase("true");
        _outputnum = Integer.parseInt(cfg.getProperty("outputnum", "5"));
        _exempt = cfg.getProperty("exempt", "").toLowerCase().split(" ");
    }

    private Collection<UserStats> getStats(String type) {
        boolean allow;
        String name;
        String files = null;
        String bytes = null;
        ArrayList<User> initialUsers = new ArrayList<>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());
        ArrayList<UserStats> outputUsers = new ArrayList<>();

        for (Iterator<User> iter = initialUsers.iterator(); iter.hasNext(); ) {
            User user = iter.next();
            allow = true;
            for (String a_exempt : _exempt) {
                if (user.isMemberOf(a_exempt))
                    allow = false;
            }
            if (user.isDeleted()) {
                allow = false;
            }
            if (!allow) {
                iter.remove();
            }
        }

        initialUsers.sort(new UserComparator(type));

        for (int i = 0; ((i < _outputnum) && (i < initialUsers.size())); ++i) {
            switch (type) {
                case "dayup" -> {
                    if ((initialUsers.get(i).getUploadedBytesDay() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesDay());
                    files = String.valueOf(initialUsers.get(i).getUploadedFilesDay());
                }
                case "daydn" -> {
                    if ((initialUsers.get(i).getDownloadedBytesDay() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesDay());
                    files = String.valueOf(initialUsers.get(i).getDownloadedFilesDay());
                }
                case "wkup" -> {
                    if ((initialUsers.get(i).getUploadedBytesWeek() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesWeek());
                    files = String.valueOf(initialUsers.get(i).getUploadedFilesWeek());
                }
                case "wkdn" -> {
                    if ((initialUsers.get(i).getDownloadedBytesWeek() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesWeek());
                    files = String.valueOf(initialUsers.get(i).getDownloadedFilesWeek());
                }
                case "monthup" -> {
                    if ((initialUsers.get(i).getUploadedBytesMonth() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesMonth());
                    files = String.valueOf(initialUsers.get(i).getUploadedFilesMonth());
                }
                case "monthdn" -> {
                    if ((initialUsers.get(i).getDownloadedBytesMonth() < 1) && (!_showzero))
                        continue;
                    bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesMonth());
                    files = String.valueOf(initialUsers.get(i).getDownloadedFilesMonth());
                }
            }
            name = initialUsers.get(i).getName();
            outputUsers.add(new UserStats(name, files, bytes));
        }

        return outputUsers;
    }

    public void resetDay(Date d) {
        GlobalContext.getEventService().publishAsync(new StatsEvent("dayup", getStats("dayup")));
        GlobalContext.getEventService().publishAsync(new StatsEvent("daydn", getStats("daydn")));
    }

    public void resetWeek(Date d) {
        GlobalContext.getEventService().publishAsync(new StatsEvent("wkup", getStats("wkup")));
        GlobalContext.getEventService().publishAsync(new StatsEvent("wkdn", getStats("wkdn")));
        // Uncomment this if you want day stats at the end of the week also
        // resetDay(d)
    }

    public void resetMonth(Date d) {
        GlobalContext.getEventService().publishAsync(new StatsEvent("monthup", getStats("monthup")));
        GlobalContext.getEventService().publishAsync(new StatsEvent("monthdn", getStats("monthdn")));
        // Uncomment this if you want day stats at the end of the month also.
        // If you want week stats when the end of a week coincides with the end of a month
        // then you'll have to do some work on the date passed to check this and then
        // conditionally call resetWeek() based on the result
        // resetDay(d);
    }

    public void resetYear(Date d) {
        // We don't do year stats so do the usual monthstats instead
        resetMonth(d);
    }

    public void resetHour(Date d) {
        // No need for this interval
        // Uncomment the rest to test output
		/*resetDay(d);
		resetWeek(d);
		resetMonth(d);*/
    }

    @EventSubscriber
    public void onReloadEvent() {
        logger.info("Received reload event, reloading");
        loadConf();
    }
}
