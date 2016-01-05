package org.drftpd.plugins.sitebot.plugins.sysop.announce;

import java.util.ResourceBundle;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.plugins.sysop.event.SysopEvent;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

public class SysopAnnouncer extends AbstractAnnouncer {

	private AnnounceConfig _config;
	private ResourceBundle _bundle;
	private String _keyPrefix;

	public String[] getEventTypes() {
		return new String[] { "sysop" };
	}

	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
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
					sayOutput(ReplacerUtils.jprintf(_keyPrefix + ".login.success",
							env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf(_keyPrefix + ".login.failed",
							env, _bundle), writer);
				}
			} else {
				if (event.isSuccessful()) {
					sayOutput(ReplacerUtils.jprintf(_keyPrefix + ".success",
							env, _bundle), writer);
				} else {
					sayOutput(ReplacerUtils.jprintf(_keyPrefix + ".failed",
							env, _bundle), writer);
				}
			}
		}
	}
}
