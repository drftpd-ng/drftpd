/*
 * Created on 2003-jul-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

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

	public FtpConfig(String cfgFileName)
		throws FileNotFoundException, IOException {
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
		//TODO read drftpd.conf.xml
	}
	public void loadConfig(Properties cfg) {
		freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}

	public void welcomeMessage(FtpResponse response) throws IOException {
		response.addComment(
			new BufferedReader(new FileReader("ftp-data/text/welcome.txt")));
	}
	
	public void directoryMessage(FtpResponse response, User user, LinkedRemoteFile dir) throws IOException {
		response.addComment("Directory message for "+dir.getPath());
	}
}
