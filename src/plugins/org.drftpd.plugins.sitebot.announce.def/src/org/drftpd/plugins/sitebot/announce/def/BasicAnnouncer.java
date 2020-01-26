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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.slavemanagement.SlaveManagement;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.SlaveEvent;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.config.ChannelConfig;
import org.drftpd.plugins.sitebot.event.InviteEvent;
import org.drftpd.event.MasterEvent;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;
import org.drftpd.GlobalContext;

/**
 * @author djb61
 * @version $Id$
 */
public class BasicAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = LogManager.getLogger(BasicAnnouncer.class);

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
	}

	public String[] getEventTypes() {
		return new String[]{"mkdir","rmdir","wipe","addslave",
				"delslave","msgslave", "msgmaster","invite"};
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent direvent) {
		if ("MKD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "mkdir");
		} else if ("RMD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "rmdir");
		} else if ("WIPE".equals(direvent.getCommand())) {
			if (direvent.getDirectory().isDirectory()) {
				outputDirectoryEvent(direvent, "wipe");
			}
		}
	}

	@EventSubscriber
	public void onSlaveEvent(SlaveEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("slave", event.getRSlave().getName());
		env.add("message", event.getMessage());

        switch (event.getCommand()) {
            case "ADDSLAVE":
                SlaveStatus status;

                try {
                    status = event.getRSlave().getSlaveStatusAvailable();
                } catch (SlaveUnavailableException e) {
                    logger.warn("in ADDSLAVE event handler", e);

                    return;
                }

                SlaveManagement.fillEnvWithSlaveStatus(env, status);

                outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix + ".addslave", env, _bundle), "addslave");
                break;
            case "DELSLAVE":
                outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix + ".delslave", env, _bundle), "delslave");
                break;
            case "MSGSLAVE":
                outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix + ".msgslave", env, _bundle), "msgslave");
                break;
        }
	}

	@EventSubscriber
	public void onMasterEvent(MasterEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("message", event.getMessage());

        outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".msgmaster", env, _bundle), "msgmaster");
	}

	@EventSubscriber
	public void onInviteEvent(InviteEvent event) {
		if (_config.getBot().getBotName().equalsIgnoreCase(event.getTargetBot())) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("user", event.getUser().getName());
			env.add("nick", event.getIrcNick());
			env.add("group", event.getUser().getGroup());
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

	private void fillEnvSection(ReplacerEnvironment env,
			DirectoryFtpEvent direvent, AnnounceWriter writer) {
		DirectoryHandle dir = direvent.getDirectory();
		env.add("user", direvent.getUser().getName());
		env.add("group", direvent.getUser().getGroup());
		env.add("section", writer.getSectionName(dir));
		env.add("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getColor());
		env.add("path", writer.getPath(dir));
	}
}
