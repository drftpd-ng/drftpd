/*
 * Created on 2003-jul-19
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.master.config;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.logging.Level;
import java.util.logging.Logger;

import net.sf.drftpd.master.FtpResponse;
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

	public FtpConfig(Properties cfg, String cfgFileName) throws IOException {
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
		loadConfig(cfg);
	}
	
	private ArrayList privpaths;
	private ArrayList msgpaths;
	private ArrayList creditloss;
	private ArrayList creditcheck;
	private void loadConfig2() throws IOException {
		ArrayList privpaths = new ArrayList();
		ArrayList msgpaths = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList creditcheck = new ArrayList();

		BufferedReader in =
			new BufferedReader(new FileReader("drftpd-0.8.conf"));
		String line;
		while ((line = in.readLine()) != null) {
			//			String args = line.split(" ");
			//			String command = args[0];
			StringTokenizer st = new StringTokenizer(line);
			if (!st.hasMoreTokens())
				continue;
			String command = st.nextToken();

			if (command.equals("privpath")) {
				String path = st.nextToken();
				privpaths.add(new PathPermission(path, makeUsers(st)));
			}
			//msgpath <path> <filename> <flag/=group/-user>
			else if (command.equals("msgpath")) {
				String path = st.nextToken();
				String messageFile = st.nextToken();
				msgpaths.add(
					new MessagePathPermission(
						path,
						messageFile,
						makeUsers(st)));
			}
			//creditloss <multiplier> <allow leechers yes/no> <path> <permissions>
			else if (command.equals("creditloss")) {
				float multiplier = Float.parseFloat(st.nextToken());
				boolean leechers = !st.nextToken().equalsIgnoreCase("no");
				String path = st.nextToken();
				Collection users = makeUsers(st);
				//TODO leecheres? ignore it? remove it from config?
				creditloss.add(
					new RatioPathPermission(multiplier, path, users));
			}
			//creditcheck <path> <ratio> [<-user|=group|flag> ...]
			else if (command.equals("creditcheck")) {
				float multiplier = Float.parseFloat(st.nextToken());
				String path = st.nextToken();
				Collection users = makeUsers(st);
				creditloss.add(
					new RatioPathPermission(multiplier, path, users));
			}
		}
		this.privpaths = privpaths;
		this.msgpaths = msgpaths;
	}
	private ArrayList makeUsers(StringTokenizer st) {
		ArrayList users = new ArrayList();
		while (st.hasMoreTokens()) {
			users.add(st.nextToken());
		}
		return users;
	}
	public boolean isVisible(User user, LinkedRemoteFile path) {
		for (Iterator iter = privpaths.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				System.out.println(
					"check path "
						+ path.getPath()
						+ " for "
						+ user.getUsername()
						+ ": "
						+ perm.check(user));
				return perm.check(user);
			}
		}
		return true;
	}
	public void loadConfig(Properties cfg) throws IOException {
		loadConfig2();
		freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}

	public void welcomeMessage(FtpResponse response) throws IOException {
		response.addComment(
			new BufferedReader(new FileReader("ftp-data/text/welcome.txt")));
	}

	public void directoryMessage(
		FtpResponse response,
		User user,
		LinkedRemoteFile dir) {
		String path = dir.getPath();

		for (Iterator iter = msgpaths.iterator(); iter.hasNext();) {
			MessagePathPermission perm = (MessagePathPermission) iter.next();
			if (perm.getPath().equals(path)) {
				if (perm.check(user)) {
					perm.printMessage(response);
				}
			}
		}
		response.addComment("Directory message for " + dir.getPath());
	}

	/**
	 * return true if file is visible + is readable by user
	 */
	public boolean hasReadPermission(User user, LinkedRemoteFile directory) {
		return isVisible(user, directory);
	}

	//	public static void main(String args[]) throws Exception {
	//		FtpConfig config = new FtpConfig("drftpd-0.7.conf");
	//		UserManager um = new JSXUserManager();
	//		User user = um.getUserByName("mog");
	//		
	//		System.out.println("isVisible: "+config.isVisible(user, "/test"));
	//	}
}
