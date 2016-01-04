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
package org.drftpd.plugins.autofreespace.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.autofreespace.event.AFSEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.InodeHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author scitz0
 */
public class AFSAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "autofreespace" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onAFSEvent(AFSEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("autofreespace");
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
            InodeHandle inode = event.getInode();
			RemoteSlave slave = event.getSlave();
			try {
				if (inode != null) {
					env.add("path", inode.getPath());
					env.add("dir", inode.getName());
					long inodeSpace = inode.getSize();
					env.add("size", Bytes.formatBytes(inodeSpace));
					env.add("date", (new SimpleDateFormat("MM/dd/yy h:mma")).format(new Date(inode.lastModified())));
				}
				env.add("slave", slave.getName());
				long slaveSpace = slave.getSlaveStatus().getDiskSpaceAvailable();
				env.add("slavesize", Bytes.formatBytes(slaveSpace));
			} catch (FileNotFoundException e) {
				// Hmm, file deleted?
			} catch (SlaveUnavailableException e) {
				// Slave went offline, announce anyway.
			}
			if (inode != null) {
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+"afs.delete", env, _bundle), writer);
			} else {
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+"afs.announce", env, _bundle), writer);
			}
		}
	}

}
