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
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpResponse;
import net.sf.drftpd.master.SlaveManagerImpl;
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
		logger.setLevel(Level.ALL);
	}

	String cfgFileName;

	public FtpConfig(String cfgFileName, ConnectionManager connManager)
		throws FileNotFoundException, IOException {
		this.cfgFileName = cfgFileName;
		this.connManager = connManager;
		reloadConfig();
	}

	/**
	 * Constructor that allows reusing of cfg object
	 * 
	 */
	public FtpConfig(Properties cfg, String cfgFileName, ConnectionManager connManager) throws IOException {
		this.cfgFileName = cfgFileName;
		loadConfig(cfg, connManager);
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
	public void reloadConfig() throws FileNotFoundException, IOException {
		Properties cfg = new Properties();
		cfg.load(new FileInputStream(cfgFileName));
		loadConfig(cfg, connManager);
	}
	
	private ArrayList _creditloss;
	private ArrayList _creditcheck;
	private ArrayList _hideinwhos;
	private ArrayList _msgpaths;
	private ArrayList _privpaths;
	private ArrayList _eventplugins;
	private ConnectionManager connManager;
	
	private void loadConfig2() throws IOException {
		ArrayList privpaths = new ArrayList();
		ArrayList msgpaths = new ArrayList();
		ArrayList hideinwhos = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList creditcheck = new ArrayList();
		ArrayList eventplugins = new ArrayList();
		
		BufferedReader in =
			new BufferedReader(new FileReader("drftpd-0.8.conf"));
		int lineno = 0;
		String line;
		while ((line = in.readLine()) != null) {
			lineno++;
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
			//creditloss <multiplier> <path> <permissions>
			else if (command.equals("creditloss")) {
				float multiplier = Float.parseFloat(st.nextToken());
				
				String path = st.nextToken();
				Collection users = makeUsers(st);
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
			} else if(command.equals("hideinwho")) {
				String path = st.nextToken();
				hideinwhos.add(new PathPermission(path, makeUsers(st)));
			} else if(command.equals("loadplugin")) {
				String clazz = st.nextToken();
				ArrayList argsCollection = new ArrayList();
				while(st.hasMoreTokens())  {
					argsCollection.add(st.nextToken());
				}
				String args[] = (String[])argsCollection.toArray(new String[0]);
				try {
					Class SIG[] = {FtpConfig.class, ConnectionManager.class, String[].class };
					Constructor met = Class.forName(clazz).getConstructor(SIG);
					Object obj = met.newInstance(new Object[] {this, connManager, args});
					eventplugins.add(obj);
				} catch (Throwable e) {
					logger.log(Level.FATAL, "Error loading "+clazz, e);
				}
			}
		}
		
		creditloss.trimToSize();
		_creditloss = creditloss;

		creditcheck.trimToSize();
		_creditcheck = creditcheck;

		privpaths.trimToSize();
		_privpaths = privpaths;
		
		msgpaths.trimToSize();
		_msgpaths = msgpaths;

		hideinwhos.trimToSize();
		_hideinwhos = hideinwhos;
		
		eventplugins.trimToSize();
		_eventplugins = eventplugins;
	}
	private ArrayList makeUsers(StringTokenizer st) {
		ArrayList users = new ArrayList();
		while (st.hasMoreTokens()) {
			users.add(st.nextToken());
		}
		return users;
	}
	public boolean isVisible(User user, LinkedRemoteFile path) {
		for (Iterator iter = _privpaths.iterator(); iter.hasNext();) {
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
	
	public boolean checkHideInWho(LinkedRemoteFile path) {
		
		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if(perm.checkPath(path)) {
				return true;
			}
		}
		return false;
	}

	public boolean checkHideInWho(LinkedRemoteFile path, User fromUser) {
		
		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if(perm.checkPath(path)) {
				return !perm.check(fromUser);
			}
		}
		return false;
	}

	public SlaveManagerImpl getSlaveManager() {
		return this.connManager.getSlavemanager();
	}
	public void loadConfig(Properties cfg, ConnectionManager connManager) throws IOException {
		loadConfig2();
		this.connManager = connManager;
		this.freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
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

		for (Iterator iter = _msgpaths.iterator(); iter.hasNext();) {
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
