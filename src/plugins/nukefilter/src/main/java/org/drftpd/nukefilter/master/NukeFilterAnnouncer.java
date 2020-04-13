package org.drftpd.nukefilter.master;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.nukefilter.master.event.NukeFilterEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author phew
 */
public class NukeFilterAnnouncer extends AbstractAnnouncer {
	
	private AnnounceConfig _config;
	private ResourceBundle _bundle;


	public String[] getEventTypes() {
		return new String[] { "nukefilter" };
	}

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;

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
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			env.put("dir", nfni.getDirectoryName());
			env.put("path", nfni.getPath());
			env.put("delay", String.valueOf(nfni.getDelay()));
			env.put("section", nfni.getSectionName());
			env.put("sectioncolor", nfni.getSectionColor());
			env.put("element", nfni.getElement());
			env.put("nukex", String.valueOf(nfni.getNukex()));
			sayOutput(ReplacerUtils.jprintf(event.getIRCString(), env, _bundle), writer);
		}
	}

}
