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
package org.drftpd.commands.autonuke.announce;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commands.autonuke.NukeItem;
import org.drftpd.commands.autonuke.event.AutoNukeEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

import java.io.FileNotFoundException;
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
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("dir", dir.getName());
			env.add("path", dir.getPath());
			SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			try {
				env.add("user", dir.getUsername());
				env.add("group", dir.getGroup());
				env.add("dirsize", Bytes.formatBytes(dir.getSize()));
				env.add("timeleft", Time.formatTime(ni.getTime() - System.currentTimeMillis()));
			} catch (FileNotFoundException e) {
                logger.warn("AutoNukeAnnouncer: Dir gone :( - {}", dir.getPath());
			}
			int i = 1;
			for (String var : event.getData()) {
				env.add("var"+i, var);
				i++;
			}
			try {
				sayOutput(SimplePrintf.jprintf(event.getIRCString(), env), writer);
			} catch (FormatterException e) {
                logger.warn("Error in irc format: {}", event.getIRCString(), e);
			}
		}
	}
}
