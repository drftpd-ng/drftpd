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

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpResponse;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;

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
	private ArrayList _eventplugin;
	private ArrayList _hideinwho;
	private ArrayList _makedir;
	private ArrayList _msgpath;
	private ArrayList _pre;
	private ArrayList _privpath;
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

	public boolean checkHideInWho(LinkedRemoteFile path, User fromUser) {
		return checkPathPermssion(path, fromUser, _hideinwho.iterator());
		//		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
		//			StringPathPermission perm = (PathPermission) iter.next();
		//			if (perm.checkPath(path)) {
		//				return !perm.check(fromUser);
		//			}
		//		}
		//		return false;
	}
	/**
	 * @param path
	 * @param fromUser
	 * @return Default: true, allowed
	 * If user matches permission, true, if not, false.
	 */
	public boolean checkMakeDir(LinkedRemoteFile path, User fromUser) {
		return checkPathPermssion(path, fromUser, _makedir.iterator());
		//		for (Iterator iter = _makedir.iterator(); iter.hasNext();) {
		//			PathPermission perm = (PathPermission) iter.next();
		//			if(perm.checkPath(path)) {
		//				return perm.check(fromUser);
		//			}
		//		}
		//		return true;
	}
	public boolean checkUpload(LinkedRemoteFile path, User fromUser) {
		return checkPathPermssion(path, fromUser, _upload.iterator());
	}

	private boolean checkPathPermssion(
		LinkedRemoteFile path,
		User fromUser,
		Iterator iter) {
		for (; iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				logger.debug(
					"checkPathPermission("
						+ path.getPath()
						+ ") ret "
						+ perm.check(fromUser));
				return perm.check(fromUser);
			}
		}
		return false;
	}

	public boolean checkPre(LinkedRemoteFile path, User fromUser) {
		return checkPathPermssion(path, fromUser, _pre.iterator());
	}

	public boolean checkPrivPath(User user, LinkedRemoteFile path) {
		for (Iterator iter = _privpath.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				return perm.check(user);
			}
		}
		return true;
	}

	public void directoryMessage(
		FtpResponse response,
		User user,
		LinkedRemoteFile dir) {

		for (Iterator iter = _msgpath.iterator(); iter.hasNext();) {
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
		return checkPrivPath(user, directory);
	}
	public void loadConfig(Properties cfg, ConnectionManager connManager)
		throws IOException {
		loadConfig2();
		this.connManager = connManager;
		this.freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}
	
	private String newConf = "perms.conf";
	private void loadConfig2() throws IOException {
		ArrayList privpath = new ArrayList();
		ArrayList msgpath = new ArrayList();
		ArrayList hideinwho = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList creditcheck = new ArrayList();
		ArrayList eventplugin = new ArrayList();
		ArrayList pre = new ArrayList();
		ArrayList upload = new ArrayList();
		//ArrayList rename = new ArrayList();
		ArrayList makedirs = new ArrayList();

		LineNumberReader in =
			new LineNumberReader(new FileReader(newConf));
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
					privpath.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				}
				//msgpath <path> <filename> <flag/=group/-user>
				else if (command.equals("msgpath")) {
					String path = st.nextToken();
					String messageFile = st.nextToken();
					msgpath.add(
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
					hideinwho.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				} else if (command.equals("makedir")) {
					makedirs.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				} else if (command.equals("pre")) {
					pre.add(
						new PatternPathPermission(
							globComiler.compile(st.nextToken()),
							makeUsers(st)));
				} else if (command.equals("upload")) {
					upload.add(
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
						eventplugin.add(obj);
					} catch (Throwable e) {
						logger.log(Level.FATAL, "Error loading " + clazz, e);
					}
				}
			} catch (Exception e) {
				logger.warn(
					"Exception when reading "+newConf+" line "
						+ in.getLineNumber(),
					e);
			}
		}

		makedirs.trimToSize();
		_makedir = makedirs;

		creditloss.trimToSize();
		_creditloss = creditloss;

		creditcheck.trimToSize();
		_creditcheck = creditcheck;

		privpath.trimToSize();
		_privpath = privpath;

		msgpath.trimToSize();
		_msgpath = msgpath;

		hideinwho.trimToSize();
		_hideinwho = hideinwho;

		eventplugin.trimToSize();
		_eventplugin = eventplugin;

		pre.trimToSize();
		_pre = pre;
		
		upload.trimToSize();
		_upload = upload;
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
}
