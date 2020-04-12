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
package org.drftpd.master.plugins.prebw.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.plugins.prebw.PreInfo;
import org.drftpd.master.plugins.prebw.UserInfo;
import org.drftpd.master.plugins.prebw.event.PREBWEvent;
import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;

import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author lh
 */
public class PREBWAnnouncer extends AbstractAnnouncer {

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
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			SectionInterface section = preInfo.getSection();
			env.put("dir", dir.getName());
			env.put("section", section.getName());
			env.put("sectioncolor", section.getColor());
			StringBuilder bw = new StringBuilder();
			String delim = ReplacerUtils.jprintf("prebw.bw.separator", env, _bundle).trim();
			for (String messure : preInfo.getMessures().keySet()) {
				Map<String, Object> tmpenv = new HashMap<>(SiteBot.GLOBAL_ENV);
				tmpenv.put("time", messure);
				tmpenv.put("speed", preInfo.getMessures().get(messure));
				if (bw.length() != 0) {
					bw.append(" ");
					bw.append(delim);
					bw.append(" ");
				}
				bw.append(ReplacerUtils.jprintf("prebw.bw.format", tmpenv, _bundle));
			}
			env.put("bw", bw);
			StringBuilder leechers = new StringBuilder();
			if (event.getLeechtopCount() != 0) {
				if (preInfo.getUsers().isEmpty()) {
					Map<String, Object> tmpenv = new HashMap<>(SiteBot.GLOBAL_ENV);
					tmpenv.put("dir", dir.getName());
					tmpenv.put("section", section.getName());
					tmpenv.put("sectioncolor", section.getColor());
					leechers.append(ReplacerUtils.jprintf( "prebw.leechtop.empty",
							tmpenv, _bundle));
				} else {
					preInfo.getUsers().sort(new UserComparator());
					int i = 0;
					for (UserInfo u : preInfo.getUsers()) {
						if (i == event.getLeechtopCount())
							break;
						Map<String, Object> tmpenv = new HashMap<>(SiteBot.GLOBAL_ENV);
						tmpenv.put("dir", dir);
						tmpenv.put("section", section.getName());
						tmpenv.put("sectioncolor", section.getColor());
						tmpenv.put("username", u.getName());
						tmpenv.put("group", u.getGroup());
						tmpenv.put("bytes", Bytes.formatBytes(u.getBytes()));
						tmpenv.put("files", u.getFiles());
						tmpenv.put("avgspeed", Bytes.formatBytes(u.getAvgSpeed()) + "/s");
						tmpenv.put("topspeed", Bytes.formatBytes(u.getTopSpeed()) + "/s");
						leechers.append(ReplacerUtils.jprintf( "prebw.leechtop.format",
								tmpenv, _bundle));
						i++;
					}
				}
			}
			env.put("leechtop", leechers);
			env.put("users", preInfo.getUsers().size());
			env.put("groups", preInfo.getGroups().size());
			env.put("bytes", Bytes.formatBytes(preInfo.getBytes()));
			env.put("messuretime", preInfo.getMtime());
			env.put("bwavg", Bytes.formatBytes(preInfo.getBWAvg())+"/s");
			env.put("bwtop", Bytes.formatBytes(preInfo.getBWTop())+"/s");
			sayOutput(ReplacerUtils.jprintf("prebw.announce", env, _bundle), writer);
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
