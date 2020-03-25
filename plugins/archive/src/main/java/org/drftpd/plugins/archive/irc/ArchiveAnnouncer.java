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
package org.drftpd.plugins.archive.irc;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.plugins.archive.event.ArchiveFailedEvent;
import org.drftpd.plugins.archive.event.ArchiveFinishEvent;
import org.drftpd.plugins.archive.event.ArchiveStartEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;

/**
 * @author CyBeR
 * @version $Id: ArchiveAnnouncer.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class ArchiveAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "archive_finish", "archive_start", "archive_failed" };
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onArchiveStartEvent(ArchiveStartEvent event) {
		AnnounceWriter writer = _config.getPathWriter("archive_start", event.getArchiveType().getDirectory());
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
            
			env.put("type", event.getArchiveType().getClass().getSimpleName());
			env.put("rls", event.getArchiveType().getDirectory().getName());
			env.put("files", event.getJobs().size());
			env.put("srcdir", event.getArchiveType().getDirectory().getParent().getPath());

			sayOutput(ReplacerUtils.jprintf("archive.start", env, _bundle), writer);
		}
	}
    
    @EventSubscriber
	public void onArchiveFinishEvent(ArchiveFinishEvent event) {
    	AnnounceWriter writer = _config.getPathWriter("archive_finish", event.getArchiveType().getDirectory());
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
            
			env.put("type", event.getArchiveType().getClass().getSimpleName());
			env.put("rls", event.getArchiveType().getDirectory().getName());
			env.put("time", event.getArchiveTime());
			env.put("srcdir", event.getArchiveType().getDirectory().getParent().getPath());
			
			if (event.getArchiveType().getDestinationDirectory() != null) {
				env.put("destdir", event.getArchiveType().getDestinationDirectory().getParent().getPath());
				sayOutput(ReplacerUtils.jprintf("archive.finish.move", env, _bundle), writer);
			} else {
				sayOutput(ReplacerUtils.jprintf("archive.finish", env, _bundle), writer);
			}
		}
    }
    
    @EventSubscriber
	public void onArchiveFailedEvent(ArchiveFailedEvent event) {
    	AnnounceWriter writer = _config.getPathWriter("archive_failed", event.getArchiveType().getDirectory());
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
            
			env.put("type", event.getArchiveType().getClass().getSimpleName());
			env.put("rls", event.getArchiveType().getDirectory().getName());
			env.put("time", event.getArchiveTime());
			env.put("srcdir", event.getArchiveType().getDirectory().getParent().getPath());
			env.put("reason", event.getFailReason());
			
			sayOutput(ReplacerUtils.jprintf("archive.failed", env, _bundle), writer);
		}
    }

}
