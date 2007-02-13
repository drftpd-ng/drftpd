package org.drftpd.master;

import org.java.plugin.boot.Application;
import org.java.plugin.boot.ApplicationPlugin;
import org.java.plugin.util.ExtendedProperties;

public class Boot extends ApplicationPlugin implements Application {

	protected void doStart() throws Exception {
		// no-op
	}

	protected void doStop() throws Exception {
		// no-op
	}
	protected Application initApplication(final ExtendedProperties config, String[] args) throws Exception {
		return this;
	}
	public void startApplication() throws Exception {
		ConnectionManager.boot();
	}
}
