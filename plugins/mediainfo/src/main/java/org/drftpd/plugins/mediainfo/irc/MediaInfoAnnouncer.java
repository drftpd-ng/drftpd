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
package org.drftpd.plugins.mediainfo.irc;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.plugins.mediainfo.MediaInfoUtils;
import org.drftpd.plugins.mediainfo.event.MediaInfoEvent;
import org.drftpd.plugins.mediainfo.protocol.MediaInfo;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class MediaInfoAnnouncer extends AbstractAnnouncer {

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
		return new String[] { "mediainfo" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

    @EventSubscriber
	public void onMediaInfoEvent(MediaInfoEvent event) {
		DirectoryHandle dir = event.getDir();
		AnnounceWriter writer = _config.getPathWriter("mediainfo", dir);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			MediaInfo mediaInfo = event.getMediaInfo();
			env.add("dirpath",dir.getPath());
			env.add("dirname",dir.getName());
			env.add("filepath",dir.getPath()+VirtualFileSystem.separator+mediaInfo.getFileName());
			env.add("filename",mediaInfo.getFileName());
			SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
			env.add("section",sec.getName());
			env.add("sectioncolor", sec.getColor());
			String sample_ok = "";
			if (!mediaInfo.getSampleOk()) {
				env.add("real_filesize", Bytes.formatBytes(mediaInfo.getActFileSize(), true));
				if (mediaInfo.getCalFileSize() == 0L) {
					sample_ok = ReplacerUtils.jprintf("mediainfo.unreadable", env, _bundle);
				} else {
					env.add("cal_filesize", Bytes.formatBytes(mediaInfo.getCalFileSize(), true));
					sample_ok = ReplacerUtils.jprintf("mediainfo.nok", env, _bundle);
				}
			} else {
				if (!mediaInfo.getRealFormat().isEmpty()) {
					if (!mediaInfo.getRealFormat().equals("AVI")) {
						sample_ok = ReplacerUtils.jprintf("mediainfo.ok", env, _bundle);
					}
				} else if (!mediaInfo.getFileName().toLowerCase().endsWith(".avi")) {
					sample_ok = ReplacerUtils.jprintf("mediainfo.ok", env, _bundle);
				}
			}
			if (!mediaInfo.getRealFormat().isEmpty()) {
				env.add("real_format", mediaInfo.getRealFormat());
				env.add("renamed_format", mediaInfo.getUploadedFormat());
				sample_ok += " " + ReplacerUtils.jprintf("mediainfo.type.missmatch", env, _bundle);
			}
			env.add("sample_ok", sample_ok);

			String ext = MediaInfoUtils.getFileExtension(mediaInfo.getFileName());
			if (ext != null) {
				sayOutput(ReplacerUtils.jprintf("mediainfo."+ext, env, _bundle), writer);
				if (!mediaInfo.getVideoInfos().isEmpty()) {
					HashMap<String,String> videoInfo = mediaInfo.getVideoInfos().get(0);
					for (Map.Entry<String,String> field : videoInfo.entrySet()) {
						String value = field.getValue();
						value = MediaInfoUtils.fixOutput(value);
						env.add("v_"+field.getKey(), value);
					}
					if (!videoInfo.containsKey("Language")) {
						env.add("v_Language", "Unknown");
					}
				}

				if (!mediaInfo.getAudioInfos().isEmpty()) {
					HashMap<String,String> audioInfo = mediaInfo.getAudioInfos().get(0);
					for (Map.Entry<String,String> field : audioInfo.entrySet()) {
						String value = field.getValue();
						value = MediaInfoUtils.fixOutput(value);
						env.add("a_"+field.getKey(), value);
					}
					StringBuilder audioLanguages = new StringBuilder();
					for (HashMap<String,String> props : mediaInfo.getAudioInfos()) {
						if (props.containsKey("Language")) {
							audioLanguages.append(props.get("Language")).append(" | ");
						} else {
							// Found audio but with unknown language, add with Unknown as language
							audioLanguages.append("Unknown | ");
						}
					}
					if (audioLanguages.length() != 0) {
						env.add("a_Languages", audioLanguages.substring(0,audioLanguages.length()-3));
					}
				}

				StringBuilder subs = new StringBuilder();
				for (HashMap<String,String> props : mediaInfo.getSubInfos()) {
					if (props.containsKey("Language")) {
						subs.append(props.get("Language")).append(" | ");
					} else {
						// Found sub but with unknown language, add with Unknown as language
						subs.append("Unknown | ");
					}
				}
				if (subs.length() != 0) {
					env.add("s_Languages", subs.substring(0,subs.length()-3));
				}

				if (!mediaInfo.getVideoInfos().isEmpty()) {
					sayOutput(ReplacerUtils.jprintf("mediainfo."+ext+".video", env, _bundle), writer);
				}
				if (!mediaInfo.getAudioInfos().isEmpty()) {
					sayOutput(ReplacerUtils.jprintf("mediainfo."+ext+".audio", env, _bundle), writer);
				}
				if (subs.length() != 0) {
					sayOutput(ReplacerUtils.jprintf("mediainfo."+ext+".sub", env, _bundle), writer);
				}
			}
		}
	}
}
