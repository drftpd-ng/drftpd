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
package org.drftpd.plugins.prebw.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.plugins.prebw.PreInfo;
import org.drftpd.plugins.prebw.UserInfo;
import org.drftpd.plugins.prebw.event.PREBWEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.sections.SectionInterface;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.Comparator;
import java.util.ResourceBundle;

/**
 * @author lh
 */
public class PREBWAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "prebw" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onPREBWEvent(PREBWEvent event) {
		PreInfo preInfo = event.getPreInfo();
		DirectoryHandle dir = preInfo.getDir();
		AnnounceWriter writer = _config.getPathWriter("prebw", dir);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			SectionInterface section = preInfo.getSection();
			env.add("dir", dir.getName());
			env.add("section", section.getName());
			env.add("sectioncolor", section.getColor());
			StringBuilder bw = new StringBuilder();
			String delim = ReplacerUtils.jprintf(_keyPrefix+".prebw.bw.separator", env, _bundle).trim();
			for (String messure : preInfo.getMessures().keySet()) {
				ReplacerEnvironment tmpenv = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
				tmpenv.add("time", messure);
				tmpenv.add("speed", preInfo.getMessures().get(messure));
				if (bw.length() != 0) {
					bw.append(" ");
					bw.append(delim);
					bw.append(" ");
				}
				bw.append(ReplacerUtils.jprintf(_keyPrefix+".prebw.bw.format", tmpenv, _bundle));
			}
			env.add("bw", bw);
			StringBuilder leechers = new StringBuilder();
			if (event.getLeechtopCount() != 0) {
				if (preInfo.getUsers().isEmpty()) {
					ReplacerEnvironment tmpenv = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
					tmpenv.add("dir", dir.getName());
					tmpenv.add("section", section.getName());
					tmpenv.add("sectioncolor", section.getColor());
					leechers.append(ReplacerUtils.jprintf(_keyPrefix + ".prebw.leechtop.empty",
							tmpenv, _bundle));
				} else {
					preInfo.getUsers().sort(new UserComparator());
					int i = 0;
					for (UserInfo u : preInfo.getUsers()) {
						if (i == event.getLeechtopCount())
							break;
						ReplacerEnvironment tmpenv = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
						tmpenv.add("dir", dir);
						tmpenv.add("section", section.getName());
						tmpenv.add("sectioncolor", section.getColor());
						tmpenv.add("username", u.getName());
						tmpenv.add("group", u.getGroup());
						tmpenv.add("bytes", Bytes.formatBytes(u.getBytes()));
						tmpenv.add("files", u.getFiles());
						tmpenv.add("avgspeed", Bytes.formatBytes(u.getAvgSpeed()) + "/s");
						tmpenv.add("topspeed", Bytes.formatBytes(u.getTopSpeed()) + "/s");
						leechers.append(ReplacerUtils.jprintf(_keyPrefix + ".prebw.leechtop.format",
								tmpenv, _bundle));
						i++;
					}
				}
			}
			env.add("leechtop", leechers);
			env.add("users", preInfo.getUsers().size());
			env.add("groups", preInfo.getGroups().size());
			env.add("bytes", Bytes.formatBytes(preInfo.getBytes()));
			env.add("messuretime", preInfo.getMtime());
			env.add("bwavg", Bytes.formatBytes(preInfo.getBWAvg())+"/s");
			env.add("bwtop", Bytes.formatBytes(preInfo.getBWTop())+"/s");
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+".prebw.announce", env, _bundle), writer);
		}
	}

	private static class UserComparator implements Comparator<UserInfo> {
		// Compare two Users.
		public final int compare ( UserInfo a, UserInfo b )	{
			Long aLong = a.getBytes();
			Long bLong = b.getBytes();
			return bLong.compareTo(aLong);
		}
	}
}
