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
package org.drftpd.autonuke.master.announce;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.autonuke.master.NukeItem;
import org.drftpd.autonuke.master.event.AutoNukeEvent;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.util.Time;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class AutoNukeAnnouncer extends AbstractAnnouncer {
	private static final Logger logger = LogManager.getLogger(AutoNukeAnnouncer.class);

	private AnnounceConfig _config;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		// The plugin is unloading so stop asking for events
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		return new String[] { "autonuke" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
	}

    @EventSubscriber
	public void onAutoNukeEvent(AutoNukeEvent event) {
		NukeItem ni = event.getNukeItem();
		DirectoryHandle dir = ni.getDir();
		if (ni.isSubdir()) {
			dir = dir.getParent();
		}
		AnnounceWriter writer = _config.getPathWriter("autonuke", dir);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			env.put("dir", dir.getName());
			env.put("path", dir.getPath());
			SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
			env.put("section", section.getName());
			env.put("sectioncolor", section.getColor());
			try {
				env.put("user", dir.getUsername());
				env.put("group", dir.getGroup());
				env.put("dirsize", Bytes.formatBytes(dir.getSize()));
				env.put("timeleft", Time.formatTime(ni.getTime() - System.currentTimeMillis()));
			} catch (FileNotFoundException e) {
                logger.warn("AutoNukeAnnouncer: Dir gone :( - {}", dir.getPath());
			}
			int i = 1;
			for (String var : event.getData()) {
				env.put("var"+i, var);
				i++;
			}

			sayOutput(ReplacerUtils.jprintf(event.getIRCString(), env), writer);
		}
	}
}
