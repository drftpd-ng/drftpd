/*
 * Created on 2003-jul-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpConfig {
	private static Logger logger = Logger.getLogger(FtpConfig.class.getName());
	static {
		logger.setLevel(Level.FINEST);
	}
	
	String cfgFileName;
	
	public FtpConfig(String cfgFileName) throws FileNotFoundException, IOException {
		this.cfgFileName = cfgFileName;
		loadConfig();
	}
	
	public FtpConfig(Properties cfg, String cfgFileName) {
		this.cfgFileName = cfgFileName;
		loadConfig(cfg);
	}
	private long freespaceMin;
	public long getFreespaceMin() {
		return freespaceMin;
	}
	
	/**
	 * 
	 * @param cfg
	 * @throws NumberFormatException
	 */
	public void loadConfig() throws FileNotFoundException, IOException {
		Properties cfg = new Properties();
		cfg.load(new FileInputStream(cfgFileName));
	}
	public void loadConfig(Properties cfg) {
		freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}
}
