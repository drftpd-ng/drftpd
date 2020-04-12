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
package org.drftpd.master.plugins.sitebot.announce.request;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.commands.request.RequestEvent;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "request", "reqfilled", "reqdel" };
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onRequestEvent(RequestEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter(event.getCommand());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			env.put("requestroot", event.getRequestRoot().getPath());
			env.put("requestname", event.getRequestName());
			env.put("issuer", event.getCommandIssuer().getName());
			env.put("issuerGroup", event.getCommandIssuer().getGroup());
			env.put("owner", event.getRequestOwner().getName());
			env.put("ownerGroup", event.getRequestOwner().getGroup());
			sayOutput(ReplacerUtils.jprintf(event.getCommand(), env, _bundle), writer);
		}
	}
}
