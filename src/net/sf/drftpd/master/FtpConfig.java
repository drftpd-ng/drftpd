/*
 * Created on 2003-jul-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.util.Properties;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpConfig {
	public FtpConfig(Properties cfg) {
		loadConfig(cfg);
	}
	
	private long freespaceMin;
	public long getFreespaceMin() {
		return freespaceMin;
	}
	
	public void loadConfig(Properties cfg) {
		freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}
}
