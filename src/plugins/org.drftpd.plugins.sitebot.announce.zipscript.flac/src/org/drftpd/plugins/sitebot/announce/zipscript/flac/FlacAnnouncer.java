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
package org.drftpd.plugins.sitebot.announce.zipscript.flac;

import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.zipscript.flac.event.FlacEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.protocol.zipscript.flac.common.VorbisTag;
import org.drftpd.protocol.zipscript.flac.common.FlacInfo;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author norox
 */
public class FlacAnnouncer extends AbstractAnnouncer {

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
				ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
				FlacInfo flacInfo = event.getFlacInfo();
				VorbisTag vorbis = flacInfo.getVorbisTag();
				if (vorbis != null) {
					env.add("artist", vorbis.getArtist());
					env.add("genre", vorbis.getGenre());
					env.add("album", vorbis.getAlbum());
					env.add("year", vorbis.getYear());
					env.add("title", vorbis.getTitle());
					if (vorbis.getTrack() == 0) {
						env.add("track","");
					} else {
						env.add("track", vorbis.getTrack());
					}
				} else {
					env.add("artist", "unknown");
					env.add("genre", "unknown");
					env.add("album", "unknown");
					env.add("year", "unknown");
					env.add("title", "unknown");
					env.add("track", "unknown");
				}
				env.add("samplerate", flacInfo.getSamplerate());
				env.add("channels", flacInfo.getChannels());
				int runSeconds = (int)flacInfo.getRuntime();
				String runtime = "";
				if (runSeconds > 59) {
					int runMins = runSeconds / 60;
					runSeconds %= 60;
					runtime = runMins + "m ";
				}
				runtime = runtime + runSeconds + "s";
				env.add("runtime", runtime);
				env.add("path", event.getDir().getName());
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+".vorbistag", env, _bundle), writer);
			}
		}
	}
}
