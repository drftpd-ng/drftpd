package org.drftpd.plugins.sitebot.plugins.sysop.announce;

import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.plugins.sysop.event.SysopEvent;
import org.tanesha.replacer.ReplacerEnvironment;

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
			ReplacerEnvironment env = new ReplacerEnvironment(
					SiteBot.GLOBAL_ENV);
			env.add("user", event.getUsername());
			env.add("message", event.getMessage());
			env.add("response", event.getResponse());
			if (event.isLogin()) {
				if (event.isSuccessful()) {
					sayOutput(ReplacerUtils.jprintf( "login.success",
							env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf( "login.failed",
							env, _bundle), writer);
				}
			} else {
				if (event.isSuccessful()) {
					sayOutput(ReplacerUtils.jprintf( "success",
							env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf( "failed",
							env, _bundle), writer);
				}
			}
		}
	}
}
