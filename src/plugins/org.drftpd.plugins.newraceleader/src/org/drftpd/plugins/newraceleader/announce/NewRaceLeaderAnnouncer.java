/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.plugins.newraceleader.announce;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.plugins.newraceleader.event.NewRaceLeaderEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.ResourceBundle;

/**
 * @author CyBeR
 * @version $Id: NewRaceLeaderAnnouncer.java 2393 2011-04-11 20:47:51Z cyber1331 $
 */
public class NewRaceLeaderAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = LogManager.getLogger(NewRaceLeaderAnnouncer.class);

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
		AnnotationProcessor.unprocess(this);
		logger.debug("Unloaded NewRaceLeader");
	}

	public String[] getEventTypes() {
		String[] types = {"store.newraceleader"};
		return types;
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onNewRaceLeaderEvent(NewRaceLeaderEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		AnnounceWriter writer = _config.getPathWriter("store.newraceleader", event.getDirectory());
		if (writer != null) {
			env.add("section",writer.getSectionName(event.getDirectory()));
			env.add("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(event.getDirectory()).getColor());
			env.add("dir",writer.getPath(event.getDirectory()));

			env.add("path",event.getDirectory().getPath());
			env.add("filesleft", event.getFiles());
			env.add("leaduser", event.getUser());
			env.add("prevuser", event.getPrevUser());

			env.add("size",Bytes.formatBytes(event.getUploaderPosition().getBytes()));
			env.add("files", event.getUploaderPosition().getFiles());
			env.add("speed", Bytes.formatBytes(event.getUploaderPosition().getXferspeed()));
			env.add("percent", event.getFiles() / event.getUploaderPosition().getFiles());

			sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.newraceleader", env, _bundle), writer);
		}
	}
}
