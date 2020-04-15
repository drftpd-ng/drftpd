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
package org.drftpd.master.sitebot.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.nuke.NukeBeans;
import org.drftpd.master.commands.nuke.NukeEvent;
import org.drftpd.master.commands.nuke.NukeUtils;
import org.drftpd.master.commands.nuke.NukedUser;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 * @version $Id$
 */
public class NukeAnnouncer extends AbstractAnnouncer {

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
        return new String[]{"nuke", "unnuke"};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onNukeEvent(NukeEvent event) {
        String type = "NUKE".equals(event.getCommand()) ? "nuke" : "unnuke";
        StringBuilder output = new StringBuilder();

        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        DirectoryHandle nukeDir = new DirectoryHandle(event.getPath());
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir);
        env.put("section", section.getName());
        env.put("sectioncolor", section.getColor());
        env.put("dir", nukeDir.getName());
        env.put("path", event.getPath());
        env.put("relpath", event.getPath().replaceAll("/.*?" + section.getName() + "/", ""));
        env.put("user", event.getUser().getName());
        env.put("multiplier", "" + event.getMultiplier());
        env.put("nukedamount", Bytes.formatBytes(event.getNukedAmount()));
        env.put("reason", event.getReason());
        env.put("size", Bytes.formatBytes(event.getSize()));

        output.append(ReplacerUtils.jprintf(type, env, _bundle));

        for (NukedUser nukeeObj : NukeBeans.getNukeeList(event.getNukeData())) {
            Map<String, Object> nukeeenv = new HashMap<>(SiteBot.GLOBAL_ENV);
            User nukee;
            try {
                nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeObj.getUsername());
            } catch (NoSuchUserException | UserFileException e1) {
                // Unable to get user, does not exist.. skip announce for this user
                continue;
            } // Error in user file.. skip announce for this user

            nukeeenv.put("user", nukee.getName());
            nukeeenv.put("group", nukee.getGroup());
            long debt = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), event.getMultiplier());
            nukeeenv.put("nukedamount", Bytes.formatBytes(debt));
            output.append(ReplacerUtils.jprintf(type + ".nukees", nukeeenv, _bundle));
        }

        AnnounceWriter writer = _config.getPathWriter(type, nukeDir);
        // Check we got a writer back, if it is null do nothing and ignore the event
        if (writer != null) {
            sayOutput(output.toString(), writer);
        }
    }
}
