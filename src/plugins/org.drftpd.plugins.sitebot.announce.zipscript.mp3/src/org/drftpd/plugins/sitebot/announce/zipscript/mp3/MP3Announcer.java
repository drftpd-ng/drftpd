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
package org.drftpd.plugins.sitebot.announce.zipscript.mp3;

import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.zipscript.mp3.event.MP3Event;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.protocol.zipscript.mp3.common.ID3Tag;
import org.drftpd.protocol.zipscript.mp3.common.MP3Info;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;
import org.drftpd.GlobalContext;

/**
 * @author djb61
 * @version $Id$
 */
public class MP3Announcer extends AbstractAnnouncer {

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
				ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
				env.add("section", writer.getSectionName(event.getDir()));
				env.add("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(event.getDir()).getColor());
				MP3Info mp3Info = event.getMP3Info();
				ID3Tag id3 = mp3Info.getID3Tag();
				if (id3 != null) {
					env.add("artist", id3.getArtist());
					env.add("genre", id3.getGenre());
					env.add("album", id3.getAlbum());
					env.add("year", id3.getYear());
					env.add("title", id3.getTitle());
					if (id3.getTrack() == 0) {
						env.add("track","");
					} else {
						env.add("track", id3.getTrack());
					}
				} else {
					env.add("artist", "unknown");
					env.add("genre", "unknown");
					env.add("album", "unknown");
					env.add("year", "unknown");
					env.add("title", "unknown");
					env.add("track", "unknown");
				}
				env.add("bitrate", Integer.toString(mp3Info.getBitrate() / 1000) + " kbit/s " + mp3Info.getEncodingtype());
				env.add("samplerate", mp3Info.getSamplerate());
				env.add("stereomode", mp3Info.getStereoMode());
				int runSeconds = (int) (mp3Info.getRuntime() / 1000);
				String runtime = "";
				if (runSeconds > 59) {
					int runMins = runSeconds / 60;
					runSeconds %= 60;
					runtime = runMins + "m ";
				}
				runtime = runtime + runSeconds + "s";
				env.add("runtime", runtime);
				env.add("path", event.getDir().getName());
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+".id3tag", env, _bundle), writer);
			}
		}
	}
}
