/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 * 
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package net.sf.drftpd.master.config;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Hashtable;
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
 * @version $Id: FtpConfig.java,v 1.41 2004/02/16 23:46:11 mog Exp $
 */
public class FtpConfig {
	private static final Logger logger = Logger.getLogger(FtpConfig.class);

	private static Collection getCollection(Hashtable tbl, String key) {
		return (Collection) tbl.get(key);
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
	private boolean _capFirstDir;
	private boolean _capFirstFile;

	String _cfgFileName;
	private ConnectionManager _connManager;
	private ArrayList _creditcheck;
	private ArrayList _creditloss;
	private long _freespaceMin;
	private boolean _isLowerDir;
	private boolean _isLowerFile;
	private String _loginPrompt = SlaveImpl.VERSION + " http://drftpd.org";
	private int _maxUsersExempt;
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private ArrayList _msgpath;
	private Hashtable _patternPaths;
	private StringTokenizer _replaceDir;
	private StringTokenizer _replaceFile;
	private boolean _useDirNames;
	private boolean _useFileNames;

	private boolean _useIdent;
	private String newConf = "conf/perms.conf";

	/**
	 * Constructor that allows reusing of cfg object
	 */
	public FtpConfig(
		Properties cfg,
		String cfgFileName,
		ConnectionManager connManager)
		throws IOException {
		_cfgFileName = cfgFileName;
		loadConfig(cfg, connManager);
	}

	public boolean checkDelete(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("delete", fromUser, path);
	}
	public boolean checkDeleteOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("deleteown", fromUser, path);
	}

	/**
	 * Returns true if the path has dirlog enabled.
	 * @param fromUser The user who created the log event.
	 * @param path The path in question.
	 * @return true if the path has dirlog enabled.
	 */
	public boolean checkDirLog(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("dirlog", fromUser, path);
	}

	/**
	 * Also checks privpath for permission
	 * @return true if fromUser is allowed to download the file path
	 */
	public boolean checkDownload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("download", fromUser, path);
	}

	/**
	 * @return true if fromUser should be hidden
	 */
	public boolean checkHideInWho(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("hideinwho", fromUser, path);
	}

	/**
	 * @return true if fromUser is allowed to mkdir in path
	 */
	public boolean checkMakeDir(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("makedir", fromUser, path);
	}

	public boolean checkPathPermission(
		String key,
		User fromUser,
		LinkedRemoteFile path) {
		return checkPathPermission(key, fromUser, path, false);
	}

	private boolean checkPathPermission(
		String key,
		User fromUser,
		LinkedRemoteFile path,
		boolean defaults) {
		Iterator iter = ((Collection) _patternPaths.get(key)).iterator();
		while (iter.hasNext()) {
			PathPermission perm = (PathPermission) iter.next();
			if (perm.checkPath(path)) {
				return perm.check(fromUser);
			}
		}
		//return false;
		return defaults;
	}

	/**
	 * @return true if user fromUser is allowed to see path
	 */
	public boolean checkPrivPath(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("privpath", fromUser, path, true);
	}

	public boolean checkRename(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("rename", fromUser, path);
	}

	public boolean checkRenameOwn(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("renameown", fromUser, path);
	}
	/**
	 * @deprecated non-core
	 */
	public boolean checkRequest(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("request", fromUser, path);
	}
	/**
	 * @return true if fromUser is allowed to upload in directory path
	 */
	public boolean checkUpload(User fromUser, LinkedRemoteFile path) {
		return checkPathPermission("upload", fromUser, path);
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
		return _connManager.getSlaveManager();
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
		Hashtable patternPathPermission = new Hashtable();
		ArrayList creditcheck = new ArrayList();
		ArrayList creditloss = new ArrayList();
		ArrayList msgpath = new ArrayList();
		_useFileNames = false;
		_replaceFile = null;
		_useDirNames = false;
		_replaceDir = null;

		LineNumberReader in = new LineNumberReader(new FileReader(newConf));
		try {
			String line;
			while ((line = in.readLine()) != null) {
				StringTokenizer st = new StringTokenizer(line);
				if (!st.hasMoreTokens())
					continue;
				String cmd = st.nextToken();

				try {
					// login_prompt <string>
					if (cmd.equals("login_prompt")) {
						_loginPrompt = line.substring(13);
					}
					//max_users <maxUsersTotal> <maxUsersExempt>
					else if (cmd.equals("max_users")) {
						_maxUsersTotal = Integer.parseInt(st.nextToken());
						_maxUsersExempt = Integer.parseInt(st.nextToken());
					} else if (cmd.equals("dir_names")) {
						_useDirNames = true;
						_capFirstDir = st.nextToken().equals("true");
						_isLowerDir = st.nextToken().equals("lower");
						_replaceDir = st;
					} else if (cmd.equals("file_names")) {
						_useFileNames = true;
						_capFirstFile = st.nextToken().equals("true");
						_isLowerFile = st.nextToken().equals("lower");
						_replaceFile = st;
					}

					//msgpath <path> <filename> <flag/=group/-user>
					else if (cmd.equals("msgpath")) {
						String path = st.nextToken();
						String messageFile = st.nextToken();
						msgpath.add(
							new MessagePathPermission(
								path,
								messageFile,
								makeUsers(st)));
					}
					//creditloss <path> <multiplier> [<-user|=group|flag> ...]
					else if (cmd.equals("creditloss")) {
						makeRatioPermission(creditloss, st);
					}
					//creditcheck <path> <ratio> [<-user|=group|flag> ...]
					else if (cmd.equals("creditcheck")) {
						makeRatioPermission(creditcheck, st);
					} else if (cmd.equals("pathperm")) {
						makePatternPathPermission(
							patternPathPermission,
							st.nextToken(),
							st);
						//						patternPathPermission.put(
						//							st.nextToken(),
						//							makePatternPathPermission(st));
					} else if (
						cmd.equals("privpath")
							|| cmd.equals("dirlog")
							|| cmd.equals("hideinwho")
							|| cmd.equals("makedir")
							|| cmd.equals("pre")
							|| cmd.equals("upload")
							|| cmd.equals("download")
							|| cmd.equals("delete")
							|| cmd.equals("deleteown")
							|| cmd.equals("rename")
							|| cmd.equals("renameown")
							|| cmd.equals("request")) {
						makePatternPathPermission(
							patternPathPermission,
							cmd,
							st);
						//						patternPathPermission.put(
						//							cmd,
						//							makePatternPathPermission(st));
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

			msgpath.trimToSize();
			_msgpath = msgpath;

			_patternPaths = patternPathPermission;
		} finally {
			in.close();
		}
	}

	private void makePatternPathPermission(
		Hashtable patternPathPermission,
		String string,
		StringTokenizer st)
		throws MalformedPatternException {
		ArrayList perms;
		perms = (ArrayList) patternPathPermission.get(string);
		if (perms == null) {
			perms = new ArrayList();
			patternPathPermission.put(string, perms);
		}
		perms.add(
			new PatternPathPermission(
				new GlobCompiler().compile(st.nextToken()),
				makeUsers((st))));
	}

	//	private static ArrayList makePatternPermission(ArrayList arr, StringTokenizer st)
	//		throws MalformedPatternException {
	//		arr.add(
	//			new PatternPathPermission(
	//				new GlobCompiler().compile(st.nextToken()),
	//				makeUsers(st)));
	//		return arr;
	//	}

	/**
	 * 
	 * @param cfg
	 * @throws NumberFormatException
	 */
	public void reloadConfig() throws FileNotFoundException, IOException {
		Properties cfg = new Properties();
		cfg.load(new FileInputStream(_cfgFileName));
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
