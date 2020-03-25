package org.drftpd.plugins.sitebot.plugins.sysop.announce;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.plugins.sysop.event.SysopEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

public class SysopAnnouncer extends AbstractAnnouncer {

	private AnnounceConfig _config;
	private ResourceBundle _bundle;
	
	public String[] getEventTypes() {
		return new String[] { "sysop" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;

		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		AnnotationProcessor.unprocess(this);
	}

	@EventSubscriber
	public void onSysopEvent(SysopEvent event) {
		AnnounceWriter writer = _config.getSimpleWriter("sysop");
		// Check we got a writer back, if it is null do nothing and ignore the
		// event
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			env.put("user", event.getUsername());
			env.put("message", event.getMessage());
			env.put("response", event.getResponse());
			if (event.isLogin()) {
				if (event.isSuccessful()) {
					sayOutput(ReplacerUtils.jprintf( "sysop.login.success", env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf( "sysop.login.failed", env, _bundle), writer);
				}
			} else {
				if (event.isSuccessful()) {
					sayOutput(ReplacerUtils.jprintf( "sysop.success", env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf( "sysop.failed", env, _bundle), writer);
				}
			}
		}
	}
}
