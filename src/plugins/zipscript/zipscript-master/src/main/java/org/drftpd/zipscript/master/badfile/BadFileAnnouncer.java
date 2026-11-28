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
package org.drftpd.zipscript.master.badfile;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.master.RaceNameMasker;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * Announces files rejected or removed by the zipscript (bad CRC, 0 byte, not in the sfv).
 * Announce type "store.bad", only announced when configured in irc.announce.conf.
 * A file refused as not in the sfv is announced at most once per {@link #NOT_IN_SFV_COOLDOWN_MILLIS}, so a
 * client retrying the same upload does not flood the channel.
 */
public class BadFileAnnouncer extends AbstractAnnouncer {

    public static final String ANNOUNCE_TYPE = "store.bad";

    /** Minimum time between two NOT_IN_SFV announces of the same file. */
    static final long NOT_IN_SFV_COOLDOWN_MILLIS = 60_000L;

    private static final Logger logger = LogManager.getLogger(BadFileAnnouncer.class);

    private final BadFileCooldown _notInSfvCooldown = new BadFileCooldown(NOT_IN_SFV_COOLDOWN_MILLIS);

    private AnnounceConfig _config;

    private ResourceBundle _bundle;

    public void initialise(AnnounceConfig config, ResourceBundle bundle) {
        _config = config;
        _bundle = bundle;
        AnnotationProcessor.process(this);
    }

    public void stop() {
        AnnotationProcessor.unprocess(this);
    }

    public String[] getEventTypes() {
        return new String[]{ANNOUNCE_TYPE};
    }

    public void setResourceBundle(ResourceBundle bundle) {
        _bundle = bundle;
    }

    @EventSubscriber
    public void onBadFileEvent(BadFileEvent event) {
        DirectoryHandle dir = event.getDirectory();
        AnnounceWriter writer = _config.getPathWriter(ANNOUNCE_TYPE, dir);
        if (writer == null) {
            return;
        }
        if (event.getReason() == BadFileReason.NOT_IN_SFV
                && !_notInSfvCooldown.tryAnnounce(dir.getPath() + "/" + event.getFileName(), System.currentTimeMillis())) {
            return;
        }
        Object group = "";
        try {
            User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(event.getUsername());
            group = user.getGroup();
        } catch (NoSuchUserException | UserFileException e) {
            logger.debug("Unable to look up the group of {}", event.getUsername(), e);
        }
        Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
        fillEnv(env, event, group, RaceNameMasker.load(), RaceNameMasker.sectionOf(dir), writer.getSectionName(dir),
                GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getColor(),
                dir.isRoot() ? "" : writer.getPath(dir));
        sayOutput(ReplacerUtils.jprintf(event.getReason().getThemeKey(), env, _bundle), writer);
    }

    /**
     * Fills the theme variables: user, group, section, sectioncolor, path (directory), file, reason, detail.
     * The user and group are masked as configured by hideuser.* in zipscript.conf.
     */
    static void fillEnv(Map<String, Object> env, BadFileEvent event, Object group, RaceNameMasker masker,
                        String maskSection, String section, Object sectionColor, String path) {
        String groupName = group == null ? null : group.toString();
        env.put("user", masker.user(event.getUsername(), groupName, maskSection));
        env.put("group", masker.groupOf(event.getUsername(), groupName, maskSection));
        env.put("section", section);
        env.put("sectioncolor", sectionColor);
        env.put("path", path);
        env.put("file", event.getFileName());
        env.put("reason", event.getReason().name());
        env.put("detail", event.getDetail());
    }
}
