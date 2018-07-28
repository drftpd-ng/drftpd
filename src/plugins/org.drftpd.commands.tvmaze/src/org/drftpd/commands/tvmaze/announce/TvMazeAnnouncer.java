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
package org.drftpd.commands.tvmaze.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.tvmaze.event.TvMazeEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;

import java.util.ResourceBundle;

/**
 * @author lh
 */
public class TvMazeAnnouncer extends AbstractAnnouncer {

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		// The plugin is unloading so stop asking for events
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		return new String[] { "tvmaze" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onTvMazeEvent(TvMazeEvent event) {
		AnnounceWriter writer = _config.getPathWriter("tvmaze", event.getDir());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+".tvmaze.announce", event.getEnv(), _bundle), writer);
		}
	}
}
