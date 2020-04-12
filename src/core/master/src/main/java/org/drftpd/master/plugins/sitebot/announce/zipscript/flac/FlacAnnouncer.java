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
package org.drftpd.master.plugins.sitebot.announce.zipscript.flac;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.commands.zipscript.flac.event.FlacEvent;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.master.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.master.protocol.zipscript.flac.common.VorbisTag;

/**
 * @author norox
 */
public class FlacAnnouncer extends AbstractAnnouncer {

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
		String[] types = {"flac"};
		return types;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onFlacEvent(FlacEvent event) {
		AnnounceWriter writer = _config.getPathWriter("flac", event.getDir());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			// Check if this is the first flac in this dir
			if (event.isFirst()) {
				Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
				FlacInfo flacInfo = event.getFlacInfo();
				VorbisTag vorbis = flacInfo.getVorbisTag();
				if (vorbis != null) {
					env.put("artist", vorbis.getArtist());
					env.put("genre", vorbis.getGenre());
					env.put("album", vorbis.getAlbum());
					env.put("year", vorbis.getYear());
					env.put("title", vorbis.getTitle());
					if (vorbis.getTrack() == 0) {
						env.put("track","");
					} else {
						env.put("track", vorbis.getTrack());
					}
				} else {
					env.put("artist", "unknown");
					env.put("genre", "unknown");
					env.put("album", "unknown");
					env.put("year", "unknown");
					env.put("title", "unknown");
					env.put("track", "unknown");
				}
				env.put("samplerate", flacInfo.getSamplerate());
				env.put("channels", flacInfo.getChannels());
				int runSeconds = (int)flacInfo.getRuntime();
				String runtime = "";
				if (runSeconds > 59) {
					int runMins = runSeconds / 60;
					runSeconds %= 60;
					runtime = runMins + "m ";
				}
				runtime = runtime + runSeconds + "s";
				env.put("runtime", runtime);
				env.put("path", event.getDir().getName());
				sayOutput(ReplacerUtils.jprintf("vorbistag", env, _bundle), writer);
			}
		}
	}
}
