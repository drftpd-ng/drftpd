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
package org.drftpd.master.sitebot.plugins.dailystats.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.sitebot.plugins.dailystats.DailyStats;
import org.drftpd.master.sitebot.plugins.dailystats.UserStats;
import org.drftpd.master.sitebot.plugins.dailystats.event.StatsEvent;
import org.drftpd.master.util.ReplacerUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class StatsAnnouncer extends AbstractAnnouncer {

    private AnnounceConfig _config;
    private ResourceBundle _bundle;

    public void initialise(AnnounceConfig config, ResourceBundle bundle) {
        _config = config;
        _bundle = bundle;

        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public void stop() {
        // The plugin is unloading so stop asking for events
        AnnotationProcessor.unprocess(this);
    }

    public String[] getEventTypes() {
        return new String[]{"dailystats.daydn", "dailystats.dayup", "dailystats.wkdn",
                "dailystats.wkup", "dailystats.monthdn", "dailystats.monthup"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onStatsEvent(StatsEvent event) {
        String statsType = event.getType();
        AnnounceWriter writer = switch (statsType) {
            case "dayup" -> _config.getSimpleWriter("dailystats.dayup");
            case "daydn" -> _config.getSimpleWriter("dailystats.daydn");
            case "wkup" -> _config.getSimpleWriter("dailystats.wkup");
            case "wkdn" -> _config.getSimpleWriter("dailystats.wkdn");
            case "monthup" -> _config.getSimpleWriter("dailystats.monthup");
            case "monthdn" -> _config.getSimpleWriter("dailystats.monthdn");
            default -> null;
        };

        // Check we got a writer back, if it is null do nothing and ignore the event
        if (writer != null) {
            Collection<UserStats> outputStats = event.getOutputStats();
            Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
            String announceKey = "dailystats." + statsType;
            sayOutput(ReplacerUtils.jprintf(announceKey, env, _bundle), writer);
            int count = 1;
            long topTotalFiles = 0;
            long topTotalBytes = 0;
            String totalFiles = "";
            String totalBytes = "";
            for (UserStats line : outputStats) {
                if (line.getName().equals("totalStats") ) {
                    totalFiles = line.getFiles();
                    totalBytes = line.getBytes();
                } else {
                    env.put("num", count);
                    env.put("name", line.getName());
                    env.put("files", line.getFiles());
                    env.put("bytes", line.getBytes());
                    topTotalFiles += Long.parseLong(line.getFiles());
                    topTotalBytes += Bytes.parseBytes(line.getBytes());
                    sayOutput(ReplacerUtils.jprintf(announceKey + ".item", env, _bundle), writer);
                    count++;
                }
            }
            if (count == 1) {
                sayOutput(ReplacerUtils.jprintf(announceKey + ".none", env, _bundle), writer);
            } else {
                env.put("topTotalFiles", topTotalFiles);
                env.put("topTotalBytes", Bytes.formatBytes(topTotalBytes));
                sayOutput(ReplacerUtils.jprintf(announceKey + ".toptotal", env, _bundle), writer);
                env.put("totalFiles", totalFiles);
                env.put("totalBytes", totalBytes);
                sayOutput(ReplacerUtils.jprintf(announceKey + ".total", env, _bundle), writer);
            }
        }
    }
}
