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
 * @version $Id: FtpConfig.java,v 1.34 2004/01/20 16:07:17 zubov Exp $
 */
public class FtpConfig {
	private static final Logger logger = Logger.getLogger(FtpConfig.class);

	private static ArrayList makePermission(ArrayList arr, StringTokenizer st)
		throws MalformedPatternException {
		arr.add(
			new PatternPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				makeUsers(st)));
		return arr;
	}

	public static String getProperty(Properties p, String name)
		throws NullPointerException {
		String result = p.getProperty(name);
		if (result == null)
			throw new NullPointerException("Error getting setting " + name);
		return result;
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
	private ConnectionManager _connManager;
	private ArrayList _creditcheck;
	private ArrayList _creditloss;
	private ArrayList _delete;
	private ArrayList _deleteown;
	private ArrayList _dirlog;
	private ArrayList _download;
	private long _freespaceMin;
	private ArrayList _hideinwho;
	private ArrayList _makedir;
	private int _maxUsersExempt;
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private ArrayList _msgpath;
	private ArrayList _pre;
	private ArrayList _privpath;
	private ArrayList _rename;
	private ArrayList _renameown;
	private ArrayList _request;
	private ArrayList _upload;

	private boolean _useIdent;

	String cfgFileName;
	private String loginPrompt = SlaveImpl.VERSION + " http://drftpd.mog.se";
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
				logger.debug(perm);
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
				System.out.println("path matched, path = " + path.getPath());
				if (perm.check(fromUser)) {
					System.out.println(
						"user matched, user = " + fromUser.toString());
					return perm.getRatio();
				} //else {
				//					return fromUser.getRatio() == 0 ? 0 : 1;
				//				}
				// if that was true, you couldn't have different settings for
				// different users in the same directory
			}
		}
		//default credit loss ratio is 1
		System.out.println("path did not match anything, path = " + path);
		return fromUser.getRatio() == 0 ? 0 : 1;
	}
	public long getFreespaceMin() {
		return _freespaceMin;
	}
	public String getLoginPrompt() {
		return loginPrompt;
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

		LineNumberReader in = new LineNumberReader(new FileReader(newConf));
		try {
			String line;
			GlobCompiler globComiler = new GlobCompiler();
			while ((line = in.readLine()) != null) {
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
					// login_prompt <string>
					else if (command.equals("login_prompt")) {
						loginPrompt = line.substring(13);
					}
					//max_users <maxUsersTotal> <maxUsersExempt>
					else if (command.equals("max_users")) {
						_maxUsersTotal = Integer.parseInt(st.nextToken());
						_maxUsersExempt = Integer.parseInt(st.nextToken());
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

	public boolean useIdent() {
		return _useIdent;
	}

}
