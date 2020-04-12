/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.master.plugins.sitebot.announce.trafficmanager.kick;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.util.ReplacerUtils;

import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.master.plugins.trafficmanager.TrafficTypeEvent;

/**
 * @author CyBeR
 * @version $Id: TrafficKickAnnouncer.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class TrafficKickAnnouncer extends AbstractAnnouncer {

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
    }

    public String[] getEventTypes() {
        return new String[]{"trafficmanager.kick"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onTrafficTypeEvent(TrafficTypeEvent event) {
        if (event.getType().equalsIgnoreCase("kick")) {
            AnnounceWriter writer = _config.getSimpleWriter("trafficmanager.kick");
            if (writer != null) {
                Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
                env.put("nickname", event.getUser().getName());
                env.put("file", event.getFile().getName());
                env.put("path", event.getFile().getParent().getPath());
                env.put("slave", event.getSlaveName());
                env.put("transfered", Bytes.formatBytes(event.getTransfered()));
                env.put("minspeed", Bytes.formatBytes(event.getMinSpeed()) + "/s");
                env.put("speed", Bytes.formatBytes(event.getSpeed()) + "/s");

                if (event.isStor()) {
                    sayOutput(ReplacerUtils.jprintf( "traffic.kick.up", env, _bundle), writer);
                } else {
                    sayOutput(ReplacerUtils.jprintf( "traffic.kick.dn", env, _bundle), writer);
                }
            }
        }
    }
}
