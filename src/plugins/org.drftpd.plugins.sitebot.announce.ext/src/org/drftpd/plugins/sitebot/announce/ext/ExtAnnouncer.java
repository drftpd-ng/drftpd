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
package org.drftpd.plugins.sitebot.announce.ext;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.tanesha.replacer.ReplacerEnvironment;

public class ExtAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = Logger.getLogger(ExtAnnouncer.class);

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	private String[] _extensions;
	
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
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig()
				.getPropertiesForPlugin(getConfDir()+"/irc.conf");
		_extensions = cfg.getProperty("store.extensions","").toLowerCase().split("\\s");

		String[] extTypes = new String[_extensions.length];
		int i = 0;
		for (String ext : _extensions) {
			extTypes[i] = "store."+ext;
		}
		return extTypes;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent dirEvent) {
		if ("STOR".equals(dirEvent.getCommand())) {
			outputDirectorySTOR((TransferEvent) dirEvent);
		}
	}

	private void outputDirectorySTOR(TransferEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		for (String ext : _extensions) {
			if (event.getTransferFile().getName().toLowerCase().endsWith("."+ext)) {
				AnnounceWriter writer = _config.getPathWriter("store."+ext, event.getDirectory());
				if (writer != null) {
					fillEnvSection(env, event, writer);
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store."+ext, env, _bundle), writer);
				}
			}
		}
	}

	private void fillEnvSection(ReplacerEnvironment env, TransferEvent event, AnnounceWriter writer) {
		DirectoryHandle dir = event.getDirectory();
		FileHandle file = event.getTransferFile();
		env.add("user", event.getUser().getName());
		env.add("group", event.getUser().getGroup());
		env.add("section", writer.getSectionName(dir));
		env.add("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getColor());
		env.add("path", writer.getPath(dir));
		env.add("file", file.getName());
		try {
			env.add("size", Bytes.formatBytes(file.getSize()));
			long xferSpeed = 0L;
			if (file.getXfertime() > 0) {
				xferSpeed = file.getSize() / file.getXfertime();
			}
			env.add("speed",Bytes.formatBytes(xferSpeed * 1000) + "/s");
		} catch (FileNotFoundException e) {
			// The file no longer exists, just fail out of the method
		}
	}
}
