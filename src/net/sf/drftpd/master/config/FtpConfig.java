package net.sf.drftpd.master.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveImpl;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;

/**
 * @author mog
 * @version $Id: FtpConfig.java,v 1.37 2004/02/09 21:44:47 mog Exp $
 */
public class FtpConfig {
	private static final Logger logger = Logger.getLogger(FtpConfig.class);

	public static String getProperty(Properties p, String name)
		throws NullPointerException {
		String result = p.getProperty(name);
		if (result == null)
			throw new NullPointerException("Error getting setting " + name);
		return result;
	}

	private static ArrayList makePermission(ArrayList arr, StringTokenizer st)
		throws MalformedPatternException {
		arr.add(
			new PatternPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				makeUsers(st)));
		return arr;
	}

	private static ArrayList makeRatioPermission(
		ArrayList arr,
		StringTokenizer st)
		throws MalformedPatternException {
		arr.add(
			new RatioPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				Float.parseFloat(st.nextToken()),
				makeUsers(st)));
		return arr;
	}

	public static ArrayList makeUsers(StringTokenizer st) {
		ArrayList users = new ArrayList();
		while (st.hasMoreTokens()) {
			users.add(st.nextToken());
		}
		return users;
	}
	private boolean _capFirstDir;
	private boolean _capFirstFile;
	private ConnectionManager _connManager;
	private ArrayList _creditcheck;
	private ArrayList _creditloss;
	private ArrayList _delete;
	private ArrayList _deleteown;
	private ArrayList _dirlog;
	private ArrayList _download;
	private long _freespaceMin;
	private ArrayList _hideinwho;
	private boolean _isLowerDir;
	private boolean _isLowerFile;
	private String _loginPrompt = SlaveImpl.VERSION + " http://drftpd.org";
	private ArrayList _makedir;
	private int _maxUsersExempt;
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private ArrayList _msgpath;
	private ArrayList _pre;
	private ArrayList _privpath;
	private ArrayList _rename;
	private ArrayList _renameown;
	private StringTokenizer _replaceDir;
	private StringTokenizer _replaceFile;
	private ArrayList _request;
	private ArrayList _upload;
	private boolean _useDirNames;
	private boolean _useFileNames;

	private boolean _useIdent;

	String cfgFileName;
	private String newConf = "conf/perms.conf";
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

	public boolean checkDelete(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _delete.iterator());
	}

	public boolean checkDeleteOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _deleteown.iterator());
	}

	/**
	 * Returns true if the path has dirlog enabled.
	 * @param fromUser The user who created the log event.
	 * @param path The path in question.
	 * @return true if the path has dirlog enabled.
	 */
	public boolean checkDirLog(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _dirlog.iterator());
	}

	/**
	 * Also checks privpath for permission
	 * @return true if fromUser is allowed to download the file path
	 */
	public boolean checkDownload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _download.iterator());
	}

	/**
	 * @return true if fromUser should be hidden
	 */
	public boolean checkHideInWho(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _hideinwho.iterator());
	}

	/**
	 * @return true if fromUser is allowed to mkdir in path
	 */
	public boolean checkMakeDir(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _makedir.iterator());
	}

	private boolean checkPathPermission(
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
		return checkPathPermission(fromUser, path, _pre.iterator());
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

	public boolean checkRename(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _rename.iterator());
	}

	public boolean checkRenameOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _renameown.iterator());
	}
	public boolean checkRequest(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _request.iterator());
	}
	/**
	 * @return true if fromUser is allowed to upload in directory path
	 */
	public boolean checkUpload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission(fromUser, path, _upload.iterator());
	}

	public void directoryMessage(
		FtpReply response,
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
	public float getCreditCheckRatio(LinkedRemoteFile path, User fromUser) {
		for (Iterator iter = _creditcheck.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();
			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				} else {
					return fromUser.getRatio();
				}
			}
		}
		return fromUser.getRatio();
	}

	public float getCreditLossRatio(LinkedRemoteFile path, User fromUser) {
		for (Iterator iter = _creditloss.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();

			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				}
			}
		}
		//default credit loss ratio is 1
		return fromUser.getRatio() == 0 ? 0 : 1;
	}

	public String getDirName(String name) {
		if (!_useDirNames)
			return name;
		String temp = new String(name);
		if (_isLowerDir)
			temp = temp.toLowerCase();
		else
			temp = temp.toUpperCase();
		if (_capFirstDir)
			temp =
				temp.substring(0, 1).toUpperCase()
					+ temp.substring(1, temp.length());
		return replaceName(temp, _replaceDir);
	}

	public String getFileName(String name) {
		if (!_useFileNames)
			return name;
		String temp = new String(name);
		if (_isLowerFile)
			temp = temp.toLowerCase();
		else
			temp = temp.toUpperCase();
		if (_capFirstFile)
			temp =
				temp.substring(0, 1).toUpperCase()
					+ temp.substring(1, temp.length());
		return replaceName(temp, _replaceFile);
	}
	public long getFreespaceMin() {
		return _freespaceMin;
	}
	public String getLoginPrompt() {
		return _loginPrompt;
	}
	public int getMaxUsersExempt() {
		return _maxUsersExempt;
	}

	public int getMaxUsersTotal() {
		return _maxUsersTotal;
	}

	public SlaveManagerImpl getSlaveManager() {
		return this._connManager.getSlaveManager();
	}

	public void loadConfig(Properties cfg, ConnectionManager connManager)
		throws IOException {
		loadConfig2();
		_connManager = connManager;
		_freespaceMin =
			Bytes.parseBytes(FtpConfig.getProperty(cfg, "freespace.min"));

		_useIdent = cfg.getProperty("use.ident", "true").equals("true");
	}

	private void loadConfig2() throws IOException {
		ArrayList creditcheck = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList delete = new ArrayList();
		ArrayList deleteown = new ArrayList();
		ArrayList dirlog = new ArrayList();
		ArrayList download = new ArrayList();
		ArrayList hideinwho = new ArrayList();
		ArrayList makedirs = new ArrayList();
		ArrayList msgpath = new ArrayList();
		//ArrayList eventplugin = new ArrayList();
		ArrayList pre = new ArrayList();
		ArrayList privpath = new ArrayList();
		ArrayList rename = new ArrayList();
		ArrayList renameown = new ArrayList();
		ArrayList request = new ArrayList();
		ArrayList upload = new ArrayList();
		_useFileNames = false;
		_replaceFile = null;
		_useDirNames = false;
		_replaceDir = null;

		LineNumberReader in = new LineNumberReader(new FileReader(newConf));
		try {
			String line;
			GlobCompiler globCompiler = new GlobCompiler();
			while ((line = in.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				if (!st.hasMoreTokens())
					continue;
				String command = st.nextToken();

				try {
					if (command.equals("privpath")) {
						privpath.add(
							new PatternPathPermission(
								globCompiler.compile(st.nextToken()),
								makeUsers(st)));
					}
					// login_prompt <string>
					else if (command.equals("login_prompt")) {
						_loginPrompt = line.substring(13);
					}
					//max_users <maxUsersTotal> <maxUsersExempt>
					else if (command.equals("max_users")) {
						_maxUsersTotal = Integer.parseInt(st.nextToken());
						_maxUsersExempt = Integer.parseInt(st.nextToken());
					} else if (command.equals("dir_names")) {
						_useDirNames = true;
						_capFirstDir = st.nextToken().equals("true");
						_isLowerDir = st.nextToken().equals("lower");
						_replaceDir = st;
					} else if (command.equals("file_names")) {
						_useFileNames = true;
						_capFirstFile = st.nextToken().equals("true");
						_isLowerFile = st.nextToken().equals("lower");
						_replaceFile = st;
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
					//creditloss <path> <multiplier> [<-user|=group|flag> ...]
					else if (command.equals("creditloss")) {
						makeRatioPermission(creditloss, st);
					}
					//creditcheck <path> <ratio> [<-user|=group|flag> ...]
					else if (command.equals("creditcheck")) {
						makeRatioPermission(creditcheck, st);
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
					} else if (command.equals("rename")) {
						makePermission(rename, st);
					} else if (command.equals("renameown")) {
						makePermission(renameown, st);
					} else if (command.equals("request")) {
						makePermission(request, st);
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

			creditcheck.trimToSize();
			_creditcheck = creditcheck;

			creditloss.trimToSize();
			_creditloss = creditloss;

			delete.trimToSize();
			_delete = delete;

			deleteown.trimToSize();
			_deleteown = deleteown;

			dirlog.trimToSize();
			_dirlog = dirlog;

			download.trimToSize();
			_download = download;

			hideinwho.trimToSize();
			_hideinwho = hideinwho;

			makedirs.trimToSize();
			_makedir = makedirs;

			msgpath.trimToSize();
			_msgpath = msgpath;

			pre.trimToSize();
			_pre = pre;

			privpath.trimToSize();
			_privpath = privpath;

			//		eventplugin.trimToSize();
			//		_eventplugin = eventplugin;

			rename.trimToSize();
			_rename = rename;

			renameown.trimToSize();
			_renameown = renameown;

			request.trimToSize();
			_request = request;

			upload.trimToSize();
			_upload = upload;

		} finally {
			in.close();
		}
	}

	/**
	 * 
	 * @param cfg
	 * @throws NumberFormatException
	 */
	public void reloadConfig() throws FileNotFoundException, IOException {
		Properties cfg = new Properties();
		cfg.load(new FileInputStream(cfgFileName));
		loadConfig(cfg, _connManager);
	}

	private void replaceChars(
		StringBuffer source,
		Character oldChar,
		Character newChar) {
		if (newChar == null) {
			int x = 0;
			while (x < source.length()) {
				if (source.charAt(x) == oldChar.charValue()) {
					source.deleteCharAt(x);
				} else
					x++;
			}
		} else {
			int x = 0;
			while (x < source.length()) {
				if (source.charAt(x) == oldChar.charValue())
					source.setCharAt(x, newChar.charValue());
				x++;
			}
		}
	}

	private String replaceName(String source, StringTokenizer st) {
		StringBuffer sb = new StringBuffer(source);
		Character oldChar = null;
		Character newChar = null;
		while (true) {
			if (!st.hasMoreTokens())
				return sb.toString();
			String nextToken = st.nextToken();
			if (nextToken.length() == 1) {
				oldChar = new Character(nextToken.charAt(0));
				newChar = null;
			} else {
				oldChar = new Character(nextToken.charAt(0));
				newChar = new Character(nextToken.charAt(1));
			}
			replaceChars(sb, oldChar, newChar);
		}
	}

	public boolean useIdent() {
		return _useIdent;
	}

}
