package org.drftpd.plugins.nukefilter.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.plugins.nukefilter.NukeFilterNukeItem;
import org.drftpd.plugins.nukefilter.event.NukeFilterEvent;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

import java.util.ResourceBundle;

/**
 * @author phew
 */
public class NukeFilterAnnouncer extends AbstractAnnouncer {
	
	private AnnounceConfig _config;
	private ResourceBundle _bundle;
	private String _keyPrefix;

	public String[] getEventTypes() {
		return new String[] { "nukefilter" };
	}

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
		//subscribe to events
		AnnotationProcessor.process(this);
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	public void stop() {
		AnnotationProcessor.unprocess(this);
	}
	
	@EventSubscriber
	public void onNukeFilterEvent(NukeFilterEvent event) {
		NukeFilterNukeItem nfni = event.getNukeFilterNukeItem();
		AnnounceWriter writer = _config.getPathWriter("nukefilter", nfni.getDirectoryHandle());
		//no point in writing to null
		if(writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("dir", nfni.getDirectoryName());
			env.add("path", nfni.getPath());
			env.add("delay", String.valueOf(nfni.getDelay()));
			env.add("section", nfni.getSectionName());
			env.add("sectioncolor", nfni.getSectionColor());
			env.add("element", nfni.getElement());
			env.add("nukex", String.valueOf(nfni.getNukex()));
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+event.getIRCString(), env, _bundle), writer);
		}
	}

}
