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
package org.drftpd.plugins.sitebot.announce.def;

import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commands.slavemanagement.SlaveManagement;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.SlaveEvent;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.AnnounceInterface;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.config.ChannelConfig;
import org.drftpd.plugins.sitebot.event.InviteEvent;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class BasicAnnouncer implements AnnounceInterface, EventSubscriber {

	private static final Logger logger = Logger.getLogger(BasicAnnouncer.class);

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
		GlobalContext.getEventService().subscribe(DirectoryFtpEvent.class, this);
		GlobalContext.getEventService().subscribe(InviteEvent.class, this);
		GlobalContext.getEventService().subscribe(SlaveEvent.class, this);
	}

	public void stop() {
		GlobalContext.getEventService().unsubscribe(DirectoryFtpEvent.class, this);
		GlobalContext.getEventService().unsubscribe(InviteEvent.class, this);
		GlobalContext.getEventService().unsubscribe(SlaveEvent.class, this);
	}

	public String[] getEventTypes() {
		String[] types = {"mkdir","request","reqfilled","rmdir","wipe","pre","addslave",
				"delslave","store","invite"};
		return types;
	}

	public void onEvent(Object event) {
		if (event instanceof DirectoryFtpEvent) {
			handleDirectoryFtpEvent((DirectoryFtpEvent) event);
		} else if (event instanceof SlaveEvent) {
			handleSlaveEvent((SlaveEvent) event);
		} else if (event instanceof InviteEvent) {
			handleInviteEvent((InviteEvent) event);
		}
	}

	private void handleDirectoryFtpEvent(DirectoryFtpEvent direvent) {
		// TODO We should decide if we are going to check path permissions when
		// receiving an event or before sending it, including it here for now until
		// that decision is made
		//if (!GlobalContext.getConfig().
		//		checkPathPermission("dirlog", direvent.getUser(), direvent.getDirectory())) {
		//	return;
		//}

		if ("MKD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "mkdir");
		} else if ("REQUEST".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "request");
		} else if ("REQFILLED".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "reqfilled");
		} else if ("RMD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "rmdir");
		} else if ("WIPE".equals(direvent.getCommand())) {
			if (direvent.getDirectory().isDirectory()) {
				outputDirectoryEvent(direvent, "wipe");
			}
		}
	}

	private void handleSlaveEvent(SlaveEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("slave", event.getRSlave().getName());
		env.add("message", event.getMessage());

		if (event.getCommand().equals("ADDSLAVE")) {
			SlaveStatus status;

			try {
				status = event.getRSlave().getSlaveStatusAvailable();
			} catch (SlaveUnavailableException e) {
				logger.warn("in ADDSLAVE event handler", e);

				return;
			}

			SlaveManagement.fillEnvWithSlaveStatus(env, status);

			outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".addslave", env, _bundle), "addslave");
		} else if (event.getCommand().equals("DELSLAVE")) {
			outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".delslave", env, _bundle), "delslave");
		}
	}

	private void handleInviteEvent(InviteEvent event) {
		if (event.getTargetBot() == null || _config.getBot().getBotName().equalsIgnoreCase(event.getTargetBot())) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("user", event.getUser().getName());
			env.add("nick", event.getIrcNick());
			if (event.getCommand().equals("INVITE")) {
				outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".invite.success", env, _bundle), "invite");
				for (ChannelConfig chan : _config.getBot().getConfig().getChannels()) {
					if (chan.isPermitted(event.getUser())) {
						_config.getBot().sendInvite(event.getIrcNick(), chan.getName());
					}
				}
			} else if (event.getCommand().equals("BINVITE")) {
				outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".invite.failed", env, _bundle), "invite");
			}
		}
	}

	private void outputDirectoryEvent(DirectoryFtpEvent direvent, String type) {
		AnnounceWriter writer = _config.getPathWriter(type, direvent.getDirectory());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			fillEnvSection(env, direvent, writer);
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+type, env, _bundle), writer);
		}
	}

	private void outputSimpleEvent(String output, String type) {
		AnnounceWriter writer = _config.getSimpleWriter(type);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			sayOutput(output, writer);
		}
	}

	private void sayOutput(String output, AnnounceWriter writer) {
		StringTokenizer st = new StringTokenizer(output,"\n");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			for (OutputWriter oWriter : writer.getOutputWriters()) {
				oWriter.sendMessage(token);
			}
		}
	}

	private void fillEnvSection(ReplacerEnvironment env,
			DirectoryFtpEvent direvent, AnnounceWriter writer) {
		DirectoryHandle dir = direvent.getDirectory();
		env.add("user", direvent.getUser().getName());
		env.add("group", direvent.getUser().getGroup());
		env.add("section", writer.getSectionName(dir));
		env.add("path", writer.getPath(dir));
	}
}
