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
package org.drftpd.raceleader.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.raceleader.master.event.NewRaceLeaderEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeaderAnnouncer.java 2393 2011-04-11 20:47:51Z cyber1331 $
 */
public class NewRaceLeaderAnnouncer extends AbstractAnnouncer {

    private static final Logger logger = LogManager.getLogger(NewRaceLeaderAnnouncer.class);

    private AnnounceConfig _config;

    private ResourceBundle _bundle;

    public void initialise(AnnounceConfig config, ResourceBundle bundle) {
        _config = config;
        _bundle = bundle;

        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public void stop() {
        AnnotationProcessor.unprocess(this);
        logger.debug("Unloaded NewRaceLeader");
    }

    public String[] getEventTypes() {
        return new String[]{"store.newraceleader"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onNewRaceLeaderEvent(NewRaceLeaderEvent event) {
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        AnnounceWriter writer = _config.getPathWriter("store.newraceleader", event.getDirectory());
        if (writer != null) {
            env.put("section", writer.getSectionName(event.getDirectory()));
            env.put("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(event.getDirectory()).getColor());
            env.put("dir", writer.getPath(event.getDirectory()));

            env.put("path", event.getDirectory().getPath());
            env.put("filesleft", event.getFiles());
            env.put("leaduser", event.getUser());
            env.put("prevuser", event.getPrevUser());

            env.put("size", Bytes.formatBytes(event.getUploaderPosition().getBytes()));
            env.put("files", event.getUploaderPosition().getFiles());
            env.put("speed", Bytes.formatBytes(event.getUploaderPosition().getXferspeed()));
            env.put("percent", event.getFiles() / event.getUploaderPosition().getFiles());

            sayOutput(ReplacerUtils.jprintf("store.newraceleader", env, _bundle), writer);
        }
    }
}
