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
import java.io.InputStream;
import java.io.LineNumberReader;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Map;
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
import org.apache.oro.text.regex.MalformedPatternException;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerFormat;

/**
 * @author <a href="mailto:drftpd@mog.se">Morgan Christiansson</a>
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class FtpConfig {
	private Map replacerFormats;
	private static Logger logger = Logger.getLogger(FtpConfig.class);
	private ArrayList _creditcheck;

	private ArrayList _creditloss;
	private ArrayList _delete;
	private ArrayList _deleteown;
	private ArrayList _download;
//	private ArrayList _eventplugin;
	private ArrayList _hideinwho;
	private ArrayList _makedir;
	private ArrayList _msgpath;
	private ArrayList _pre;
	private ArrayList _privpath;
	private ArrayList _upload;
	private ArrayList _dirlog;

	String cfgFileName;
	private ConnectionManager connManager;
	private long freespaceMin;

	private String newConf = "perms.conf";

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

	/**
	 * @param _user
	 * @param requestedFile
	 * @return
	 */
	public boolean checkDelete(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _delete.iterator());
	}

	public boolean checkDirLog(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _dirlog.iterator());
	}
	/**
	 * Also checks privpath for permission
	 * @return true if fromUser is allowed to download the file path
	 */
	public boolean checkDownload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _download.iterator());
	}

	/**
	 * @return true if fromUser should be hidden
	 */
	public boolean checkHideInWho(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _hideinwho.iterator());
		//		for (Iterator iter = _hideinwhos.iterator(); iter.hasNext();) {
		//			StringPathPermission perm = (PathPermission) iter.next();
		//			if (perm.checkPath(path)) {
		//				return !perm.check(fromUser);
		//			}
		//		}
		//		return false;
	}
	/**
	 * @return true if fromUser is allowed to mkdir in path
	 */
	public boolean checkMakeDir(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _makedir.iterator());
		//		for (Iterator iter = _makedir.iterator(); iter.hasNext();) {
		//			PathPermission perm = (PathPermission) iter.next();
		//			if(perm.checkPath(path)) {
		//				return perm.check(fromUser);
		//			}
		//		}
		//		return true;
	}

	private boolean checkPathPermssion(
		User fromUser,
		LinkedRemoteFile path,
		Iterator iter) {
		while (iter.hasNext()) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				return perm.check(fromUser);
			}
		}
		return false;
	}

	/**
	 * @return true if fromUser is allowed to pre in path
	 */
	public boolean checkPre(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _pre.iterator());
	}

	/**
	 * @return true if user fromUser is allowed to see path
	 */
	public boolean checkPrivPath(User fromUser, LinkedRemoteFile path) {
		for (Iterator iter = _privpath.iterator(); iter.hasNext();) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				// path matched, if user is in ACL he's allowed access
				return perm.check(fromUser);
			}
		}
		// default is to allow access
		return true;
	}

	/**
	 * @return true if fromUser is allowed to upload in directory path
	 */
	public boolean checkUpload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermssion(fromUser, path, _upload.iterator());
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
	}

	public float getCreditLossRatio(LinkedRemoteFile path, User fromUser) {
		for (Iterator iter = _creditloss.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();
			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				} else {
					return 1;
				}
			}
		}
		//default credit loss ratio is 1
		return 1;
	}
	public long getFreespaceMin() {
		return freespaceMin;
	}

	private Map loadFormats(InputStream in)
		throws FormatterException, IOException {
		Properties props = new Properties();
		props.load(in);
		Hashtable replacerFormats = new Hashtable();
		for (Iterator iter = props.entrySet().iterator(); iter.hasNext();) {
			Map.Entry entry = (Map.Entry) iter.next();
			replacerFormats.put(
				(String) entry.getKey(),
				ReplacerFormat.createFormat((String) entry.getValue()));
		}
		return replacerFormats;
	}

	public ReplacerFormat getReplacerFormat(String key) {
		ReplacerFormat ret = (ReplacerFormat) replacerFormats.get(key);
		if (ret == null)
			throw new NoSuchFieldError("No ReplacerFormat for " + key);
		return ret;
	}

	public SlaveManagerImpl getSlaveManager() {
		return this.connManager.getSlaveManager();
	}

	/**
	 * return true if file is visible + is readable by user
	 * @deprecated
	 */
	public boolean hasReadPermission(User user, LinkedRemoteFile directory) {
		return checkPrivPath(user, directory);
	}
	public void loadConfig(Properties cfg, ConnectionManager connManager)
		throws IOException {
		loadConfig2();
		try {
			replacerFormats =
				loadFormats(new FileInputStream("replacerformats.conf"));
		} catch (FormatterException e) {
			throw (IOException) new IOException().initCause(e);
		}
		this.connManager = connManager;
		this.freespaceMin = Long.parseLong(cfg.getProperty("freespace.min"));
	}
	private void loadConfig2() throws IOException {
		ArrayList privpath = new ArrayList();
		ArrayList msgpath = new ArrayList();
		ArrayList hideinwho = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList creditcheck = new ArrayList();
		//ArrayList eventplugin = new ArrayList();
		ArrayList pre = new ArrayList();
		ArrayList upload = new ArrayList();
		ArrayList makedirs = new ArrayList();
		ArrayList download = new ArrayList();
		ArrayList delete = new ArrayList();
		ArrayList deleteown = new ArrayList();
		ArrayList dirlog = new ArrayList();

		LineNumberReader in = new LineNumberReader(new FileReader(newConf));
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
				} else if (command.equals("dirlog")) {
					makePermission(dirlog, st);
				} else if (command.equals("hideinwho")) {
					makePermission(hideinwho, st);
				} else if (command.equals("makedir")) {
					makePermission(makedirs, st);
				} else if (command.equals("pre")) {
					makePermission(pre, st);
				} else if (command.equals("upload")) {
					makePermission(upload, st);
				} else if (command.equals("download")) {
					makePermission(download, st);
				} else if (command.equals("delete")) {
					makePermission(delete, st);
				} else if (command.equals("deleteown")) {
					makePermission(deleteown, st);
//				} else if (command.equals("plugin")) {
//					String clazz = st.nextToken();
//					ArrayList argsCollection = new ArrayList();
//					while (st.hasMoreTokens()) {
//						argsCollection.add(st.nextToken());
//					}
//					String args[] =
//						(String[]) argsCollection.toArray(new String[0]);
//					try {
//						Class SIG[] =
//							{
//								FtpConfig.class,
//								ConnectionManager.class,
//								String[].class };
//						Constructor met =
//							Class.forName(clazz).getConstructor(SIG);
//						Object obj =
//							met.newInstance(
//								new Object[] { this, connManager, args });
//						eventplugin.add(obj);
//					} catch (Throwable e) {
//						logger.log(Level.FATAL, "Error loading " + clazz, e);
//					}
				}
			} catch (Exception e) {
				logger.warn(
					"Exception when reading "
						+ newConf
						+ " line "
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

//		eventplugin.trimToSize();
//		_eventplugin = eventplugin;

		pre.trimToSize();
		_pre = pre;

		upload.trimToSize();
		_upload = upload;

		download.trimToSize();
		_download = download;

		delete.trimToSize();
		_delete = delete;

		dirlog.trimToSize();
		_dirlog = dirlog;
	}

	/**
	 * @param delete
	 * @param st
	 */
	private void makePermission(ArrayList arr, StringTokenizer st)
		throws MalformedPatternException {
		arr.add(
			new PatternPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				makeUsers(st)));
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
