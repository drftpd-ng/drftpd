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
package org.drftpd.autofreespace.master;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.autofreespace.master.event.AFSEvent;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class AFSAnnouncer extends AbstractAnnouncer {

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
        return new String[]{"autofreespace"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onAFSEvent(AFSEvent event) {
        AnnounceWriter writer = _config.getSimpleWriter("autofreespace");
        // Check we got a writer back, if it is null do nothing and ignore the event
        if (writer != null) {
            Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
            InodeHandle inode = event.getInode();
            RemoteSlave slave = event.getSlave();
            try {
                if (inode != null) {
                    env.put("path", inode.getPath());
                    env.put("dir", inode.getName());
                    long inodeSpace = inode.getSize();
                    env.put("size", Bytes.formatBytes(inodeSpace));
                    env.put("date", (new SimpleDateFormat("MM/dd/yy h:mma")).format(new Date(inode.lastModified())));
                }
                env.put("slave", slave.getName());
                long slaveSpace = slave.getSlaveStatus().getDiskSpaceAvailable();
                env.put("slavesize", Bytes.formatBytes(slaveSpace));
            } catch (FileNotFoundException e) {
                // Hmm, file deleted?
            } catch (SlaveUnavailableException e) {
                // Slave went offline, announce anyway.
            }
            if (inode != null) {
                sayOutput(ReplacerUtils.jprintf("afs.delete", env, _bundle), writer);
            } else {
                sayOutput(ReplacerUtils.jprintf("afs.announce", env, _bundle), writer);
            }
        }
    }

}
