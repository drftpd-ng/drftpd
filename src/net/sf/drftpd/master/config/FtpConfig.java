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
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;

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
	private static Logger logger = Logger.getLogger(FtpConfig.class);
	private ArrayList _creditcheck;

	private ArrayList _creditloss;
	private ArrayList _eventplugins;
	private ArrayList _hideinwhos;
	private ArrayList _makedir;
	private ArrayList _msgpaths;
	private ArrayList _privpaths;
	private ArrayList _rename;
	private ArrayList _upload;

	String cfgFileName;
	private ConnectionManager connManager;
	private long freespaceMin;

	/**
	 * Constructor that allows reusing of cfg object
	 * 
	 */
	public FtpConfig(
		Properties cfg,
		String cfgFileName,
		ConnectionManager connManager)
		throws IOException {
		this.cfgFileName = cfgFileName;
		loadConfig(cfg, connManager);
	}

	public FtpConfig(String cfgFileName, ConnectionManager connManager)
		throws FileNotFoundException, IOException {
		this.cfgFileName = cfgFileName;
		this.connManager = connManager;
		reloadConfig();
	}

	public boolean checkHideInWho(LinkedRemoteFile path) {
		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
			StringPathPermission perm = (StringPathPermission) iter.next();
			if (perm.checkPath(path)) {
				return true;
			}
		}
		return false;
	}
	public boolean checkHideInWho(LinkedRemoteFile path, User fromUser) {

		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
			StringPathPermission perm = (StringPathPermission) iter.next();
			if (perm.checkPath(path)) {
				return !perm.check(fromUser);
			}
		}
		return false;
	}
	
	/**
	 * @param path
	 * @param fromUser
	 * @return Default: true, allowed
	 * If user matches permission, true, if not, false.
	 */
	public boolean checkMakeDir(LinkedRemoteFile path, User fromUser) {
		for (Iterator iter = _makedir.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if(perm.checkPath(path)) {
				return perm.check(fromUser);
			}
		}
		return true;
	}
	
	public void directoryMessage(
		FtpResponse response,
		User user,
		LinkedRemoteFile dir) {

		for (Iterator iter = _msgpaths.iterator(); iter.hasNext();) {
			MessagePathPermission perm = (MessagePathPermission) iter.next();
			if (perm.checkPath(dir)) {
				if (perm.check(user)) {
					perm.printMessage(response);
				}
			}
		}
		response.addComment("Directory message for " + dir.getPath());
	}

	public float getCreditLossRatio(LinkedRemoteFile path, User fromUser) {

		//default credit loss ratio is 1
		return 1;
	}
	public long getFreespaceMin() {
		return freespaceMin;
	}

	public SlaveManagerImpl getSlaveManager() {
		return this.connManager.getSlaveManager();
	}

	/**
	 * return true if file is visible + is readable by user
	 */
	public boolean hasReadPermission(User user, LinkedRemoteFile directory) {
		return isVisible(user, directory);
	}

	public boolean isVisible(User user, LinkedRemoteFile path) {
		for (Iterator iter = _privpaths.iterator(); iter.hasNext();) {
			StringPathPermission perm = (StringPathPermission) iter.next();
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
	public void loadConfig(Properties cfg, ConnectionManager connManager)
		throws IOException {
		loadConfig2();
		this.connManager = connManager;
		this.freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}

	private void loadConfig2() throws IOException {
		ArrayList privpaths = new ArrayList();
		ArrayList msgpaths = new ArrayList();
		ArrayList hideinwhos = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList creditcheck = new ArrayList();
		ArrayList eventplugins = new ArrayList();

		//ArrayList upload = new ArrayList();
		//ArrayList rename = new ArrayList();
		ArrayList makedirs = new ArrayList();

		LineNumberReader in =
			new LineNumberReader(new FileReader("drftpd-0.8.conf"));
		int lineno = 0;
		String line;
		GlobCompiler globComiler = new GlobCompiler();
		while ((line = in.readLine()) != null) {
			lineno++;
			//			String args = line.split(" ");
			//			String command = args[0];
			StringTokenizer st = new StringTokenizer(line);
			if (!st.hasMoreTokens())
				continue;
			String command = st.nextToken();

			try {
				if (command.equals("privpath")) {
					String path = st.nextToken();
					privpaths.add(
						new StringPathPermission(path, makeUsers(st)));
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
				} else if (command.equals("hideinwho")) {
					String path = st.nextToken();
					hideinwhos.add(
						new StringPathPermission(path, makeUsers(st)));
				} else if (command.equals("makedir")) {
					makedirs.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				} else if (command.equals("plugin")) {
					String clazz = st.nextToken();
					ArrayList argsCollection = new ArrayList();
					while (st.hasMoreTokens()) {
						argsCollection.add(st.nextToken());
					}
					String args[] =
						(String[]) argsCollection.toArray(new String[0]);
					try {
						Class SIG[] =
							{
								FtpConfig.class,
								ConnectionManager.class,
								String[].class };
						Constructor met =
							Class.forName(clazz).getConstructor(SIG);
						Object obj =
							met.newInstance(
								new Object[] { this, connManager, args });
						eventplugins.add(obj);
					} catch (Throwable e) {
						logger.log(Level.FATAL, "Error loading " + clazz, e);
					}
				}
			} catch (Exception e) {
				logger.warn("Exception when reading drftpd-0.8.conf line "+in.getLineNumber(), e);
			}
		}

		makedirs.trimToSize();
		_makedir = makedirs;
		
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

	public void welcomeMessage(FtpResponse response) throws IOException {
		response.addComment(
			new BufferedReader(new FileReader("ftp-data/text/welcome.txt")));
	}

	//	public static void main(String args[]) throws Exception {
	//		FtpConfig config = new FtpConfig("drftpd-0.7.conf");
	//		UserManager um = new JSXUserManager();
	//		User user = um.getUserByName("mog");
	//		
	//		System.out.println("isVisible: "+config.isVisible(user, "/test"));
	//	}
}
