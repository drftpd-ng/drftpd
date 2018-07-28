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
package org.drftpd.plugins.sitebot.announce.nuke;

import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.Bytes;
import org.drftpd.commands.UserManagement;
import org.drftpd.commands.nuke.NukeBeans;
import org.drftpd.commands.nuke.NukeUtils;
import org.drftpd.commands.nuke.NukedUser;
import org.drftpd.sections.SectionInterface;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.event.NukeEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author scitz0
 * @version $Id$
 */
public class NukeAnnouncer extends AbstractAnnouncer {

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName()+".";
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		// The plugin is unloading so stop asking for events
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		return new String[] { "nuke", "unnuke" };
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onNukeEvent(NukeEvent event) {
		String type = "NUKE".equals(event.getCommand()) ? "nuke" : "unnuke";
		StringBuilder output = new StringBuilder();

		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		DirectoryHandle nukeDir = new DirectoryHandle(event.getPath());
		SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(nukeDir);
		env.add("section", section.getName());
		env.add("sectioncolor", section.getColor());
		env.add("dir", nukeDir.getName());
		env.add("path", event.getPath());
		env.add("relpath", event.getPath().replaceAll("/.*?"+section.getName()+"/",""));
		env.add("user", event.getUser().getName());
		env.add("multiplier", ""+event.getMultiplier());
		env.add("nukedamount", Bytes.formatBytes(event.getNukedAmount()));
		env.add("reason", event.getReason());
		env.add("size", Bytes.formatBytes(event.getSize()));

		output.append(ReplacerUtils.jprintf(_keyPrefix+type, env, _bundle));

		for (NukedUser nukeeObj : NukeBeans.getNukeeList(event.getNukeData())) {
			ReplacerEnvironment nukeeenv = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			User nukee;
			try {
				nukee = GlobalContext.getGlobalContext().getUserManager().getUserByName(nukeeObj.getUsername());
			} catch (NoSuchUserException e1) {
                // Unable to get user, does not exist.. skip announce for this user
				continue;
            } catch (UserFileException e1) {
                // Error in user file.. skip announce for this user
				continue;
            }
			nukeeenv.add("user", nukee.getName());
			nukeeenv.add("group", nukee.getGroup());
			long debt = NukeUtils.calculateNukedAmount(nukeeObj.getAmount(),
                    nukee.getKeyedMap().getObjectFloat(UserManagement.RATIO), event.getMultiplier());
			nukeeenv.add("nukedamount", Bytes.formatBytes(debt));
			output.append(ReplacerUtils.jprintf(_keyPrefix+type+".nukees", nukeeenv, _bundle));
		}

		AnnounceWriter writer = _config.getPathWriter(type, nukeDir);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			sayOutput(output.toString(), writer);
		}
	}
}
