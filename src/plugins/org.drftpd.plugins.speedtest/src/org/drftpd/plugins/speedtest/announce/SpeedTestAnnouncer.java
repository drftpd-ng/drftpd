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
package org.drftpd.plugins.speedtest.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.Time;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.speedtest.event.SpeedTestEvent;
import org.drftpd.slave.TransferStatus;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class SpeedTestAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "speedtest" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onSpeedTestEvent(SpeedTestEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("speedtest");
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("path", event.getFilePath());
			env.add("file", VirtualFileSystem.getLast(event.getFilePath()));
			env.add("user", event.getUser().getName());
			env.add("group", event.getUser().getGroup());
			env.add("slave", event.getSlaveName());
			TransferStatus status = event.getStatus();
			env.add("size", Bytes.formatBytes(status.getTransfered()));
			env.add("time", Time.formatTime(status.getElapsed()));
			env.add("speed", Bytes.formatBytes(status.getXferSpeed())+"/s");
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+".speedtest", env, _bundle), writer);
		}
	}
}
