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
package org.drftpd.trial.master;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.trial.master.types.toptrial.TopTrialEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author CyBeR
 * @version $Id: TopTrialAnnouncer.java 2072 2010-09-18 22:01:23Z djb61 $
 */
public class TopTrialAnnouncer extends AbstractAnnouncer {

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
		return new String[] {"trialmanager.toptrial"};
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onTopTrialEvent(TopTrialEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("trialmanager.toptrial");
		if (writer != null) {
			Map<String, Object> env_header = new HashMap<>(SiteBot.GLOBAL_ENV);
			env_header.put("name",event.getName());
			env_header.put("min",event.getMin());
			env_header.put("period",event.getPeriodStr());
			
			sayOutput(ReplacerUtils.jprintf("toptrial.header", env_header, _bundle), writer);
			int passed = 0;
			ArrayList<User> users = event.getUsers();
			for (User user : users) {
				Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
				env.put("num",++passed);
				env.put("bytes", Bytes.formatBytes(user.getUploadedBytesForPeriod(event.getPeriod())));
				env.put("files",user.getUploadedFilesForPeriod(event.getPeriod()));
				env.put("name", user.getName());
				if ((user.getUploadedBytesForPeriod(event.getPeriod()) > event.getMin()) && (passed < event.getKeep())) {
					sayOutput(ReplacerUtils.jprintf("toptrial.passed", env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf("toptrial.failed", env, _bundle), writer);
				
				}
			}	
			if (passed == 0) {
				sayOutput(ReplacerUtils.jprintf("toptrial.empty", env_header, _bundle), writer);
			}
		}
	}
}
