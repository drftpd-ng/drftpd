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
package org.drftpd.master.plugins.sitebot.announce.zipscript.mp3;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.commands.zipscript.mp3.event.MP3Event;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.master.plugins.sitebot.AnnounceWriter;
import org.drftpd.master.plugins.sitebot.SiteBot;
import org.drftpd.master.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.master.protocol.zipscript.mp3.common.ID3Tag;
import org.drftpd.master.protocol.zipscript.mp3.common.MP3Info;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author djb61
 * @version $Id$
 */
public class MP3Announcer extends AbstractAnnouncer {

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
		String[] types = {"mp3"};
		return types;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onMP3Event(MP3Event event) {
		AnnounceWriter writer = _config.getPathWriter("mp3",event.getDir());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			// Check if this is the first mp3 in this dir
			if (event.isFirst()) {
				Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
				env.put("section", writer.getSectionName(event.getDir()));
				env.put("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(event.getDir()).getColor());
				MP3Info mp3Info = event.getMP3Info();
				ID3Tag id3 = mp3Info.getID3Tag();
				if (id3 != null) {
					env.put("artist", id3.getArtist());
					env.put("genre", id3.getGenre());
					env.put("album", id3.getAlbum());
					env.put("year", id3.getYear());
					env.put("title", id3.getTitle());
					if (id3.getTrack() == 0) {
						env.put("track","");
					} else {
						env.put("track", id3.getTrack());
					}
				} else {
					env.put("artist", "unknown");
					env.put("genre", "unknown");
					env.put("album", "unknown");
					env.put("year", "unknown");
					env.put("title", "unknown");
					env.put("track", "unknown");
				}
				env.put("bitrate", Integer.toString(mp3Info.getBitrate() / 1000) + " kbit/s " + mp3Info.getEncodingtype());
				env.put("samplerate", mp3Info.getSamplerate());
				env.put("stereomode", mp3Info.getStereoMode());
				int runSeconds = (int) (mp3Info.getRuntime() / 1000);
				String runtime = "";
				if (runSeconds > 59) {
					int runMins = runSeconds / 60;
					runSeconds %= 60;
					runtime = runMins + "m ";
				}
				runtime = runtime + runSeconds + "s";
				env.put("runtime", runtime);
				env.put("path", event.getDir().getName());
				sayOutput(ReplacerUtils.jprintf("id3tag", env, _bundle), writer);
			}
		}
	}
}
