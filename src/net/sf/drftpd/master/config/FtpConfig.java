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
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.io.Reader;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Observable;
import java.util.Properties;

import net.sf.drftpd.util.PortRange;

import org.apache.log4j.Logger;
import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.commands.Reply;
import org.drftpd.commands.UserManagement;
import org.drftpd.permissions.GlobPathPermission;
import org.drftpd.permissions.MessagePathPermission;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.PatternPathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.permissions.RatioPathPermission;
import org.drftpd.slave.Slave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;

import com.Ostermiller.util.StringTokenizer;

/**
 * @author mog
 * @version $Id$
 */
public class FtpConfig extends Observable implements ConfigInterface {
	private static final Logger logger = Logger.getLogger(FtpConfig.class);

	private ArrayList<InetAddress> _bouncerIps;

	private boolean _capFirstDir;

	private boolean _capFirstFile;

	private ArrayList<RatioPathPermission> _creditcheck = new ArrayList<RatioPathPermission>();

	private ArrayList<RatioPathPermission> _creditloss = new ArrayList<RatioPathPermission>();
	
	private String[] _cipherSuites;

	private boolean _hideIps;

	private boolean _isLowerDir;

	private boolean _isLowerFile;

	private String _loginPrompt = Slave.VERSION + " http://drftpd.org";

	private int _maxUsersExempt;

	private int _maxUsersTotal = Integer.MAX_VALUE;

	private ArrayList<MessagePathPermission> _msgpath = new ArrayList<MessagePathPermission>();

	private String _pasv_addr;

	private Hashtable<String, ArrayList<PathPermission>> _pathsPerms = new Hashtable<String, ArrayList<PathPermission>>();

	private Hashtable<String, Permission> _permissions = new Hashtable<String, Permission>();

	private StringTokenizer _replaceDir = null;

	private StringTokenizer _replaceFile = null;

	private boolean _useDirNames = false;

	private boolean _useFileNames = false;

	private static final String newConf = "conf/perms.conf";

	private static final String oldConf = "drftpd.conf";

	protected PortRange _portRange;

	private Permission _shutdown;

	private Properties _properties;

	private static FtpConfig _config;

	public static FtpConfig getFtpConfig() {
		if (_config == null) {
			reload();
		}
		return _config;
	}
	/**
	 * If you're creating a FtpConfig object and it's not part of a TestCase
	 * you're not doing it correctly, FtpConfig is a Singleton
	 *
	 */
	protected FtpConfig() {
		try {
			loadConfig();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	private static ArrayList makeRatioPermission(
			ArrayList<RatioPathPermission> arr, StringTokenizer st)
			throws MalformedPatternException {
		arr
				.add(new RatioPathPermission(new GlobCompiler().compile(st
						.nextToken()), Float.parseFloat(st.nextToken()),
						makeUsers(st)));

		return arr;
	}

	public Properties getProperties() {
		return _properties;
	}

	public static ArrayList<String> makeUsers(Enumeration st) {
		ArrayList<String> users = new ArrayList<String>();

		while (st.hasMoreElements()) {
			users.add((String) st.nextElement());
		}

		return users;
	}

	public boolean checkPathPermission(String key, User fromUser,
			DirectoryHandle path) {
		return checkPathPermission(key, fromUser, path, false);
	}

	public boolean checkPathPermission(String key, User fromUser,
			DirectoryHandle path, boolean defaults) {
		Collection coll = ((Collection) _pathsPerms.get(key));

		if (coll == null) {
			return defaults;
		}

		Iterator iter = coll.iterator();

		while (iter.hasNext()) {
			PathPermission perm = (PathPermission) iter.next();

			if (perm.checkPath(path)) {
				return perm.check(fromUser);
			}
		}

		return defaults;
	}

	public boolean checkPermission(String key, User user) {
		Permission perm = _permissions.get(key);

		return (perm == null) ? false : perm.check(user);
	}

	public void directoryMessage(Reply response, User user, DirectoryHandle dir) {
		for (Iterator iter = _msgpath.iterator(); iter.hasNext();) {
			MessagePathPermission perm = (MessagePathPermission) iter.next();

			if (perm.checkPath(dir)) {
				if (perm.check(user)) {
					perm.printMessage(response);
				}
			}
		}
	}

	/**
	 * @return Returns the bouncerIp.
	 */
	public List getBouncerIps() {
		return _bouncerIps;
	}

	public float getCreditCheckRatio(DirectoryHandle path, User fromUser) {
		for (Iterator iter = _creditcheck.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();

			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				}

				return fromUser.getKeyedMap().getObjectFloat(
						UserManagement.RATIO);
			}
		}

		return fromUser.getKeyedMap().getObjectFloat(UserManagement.RATIO);
	}

	public float getCreditLossRatio(DirectoryHandle path, User fromUser) {
		for (Iterator iter = _creditloss.iterator(); iter.hasNext();) {
			RatioPathPermission perm = (RatioPathPermission) iter.next();

			if (perm.checkPath(path)) {
				if (perm.check(fromUser)) {
					return perm.getRatio();
				}
			}
		}

		// default credit loss ratio is 1
		return (fromUser.getKeyedMap().getObjectFloat(UserManagement.RATIO) == 0) ? 0
				: 1;
	}
	
	public String[] getCipherSuites() {
		// returns null if none are configured explicitly
		if (_cipherSuites == null) {
			return null;
		}
		return _cipherSuites;
	}

	public String getDirName(String name) {
		if (!_useDirNames) {
			return name;
		}

		String temp = null;

		if (_isLowerDir) {
			temp = name.toLowerCase();
		} else {
			temp = name.toUpperCase();
		}

		if (_capFirstDir) {
			temp = temp.substring(0, 1).toUpperCase()
					+ temp.substring(1, temp.length());
		}

		return replaceName(temp, _replaceDir);
	}

	public String getFileName(String name) {
		if (!_useFileNames) {
			return name;
		}

		String temp = name;

		if (_isLowerFile) {
			temp = temp.toLowerCase();
		} else {
			temp = temp.toUpperCase();
		}

		if (_capFirstFile) {
			temp = temp.substring(0, 1).toUpperCase()
					+ temp.substring(1, temp.length());
		}

		return replaceName(temp, _replaceFile);
	}

	public GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public boolean getHideIps() {
		return _hideIps;
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

	public String getPasvAddress() throws NullPointerException {
		if (_pasv_addr == null)
			throw new NullPointerException();
		return _pasv_addr;
	}

	public void loadConfig() throws IOException {
		FileInputStream fis = null;
		FileReader fr = null;
		_properties = new Properties();
		try {
			fis = new FileInputStream(oldConf);
			_properties.load(fis);
			loadConfig1();
			fr = new FileReader(newConf);
			loadConfig2(fr);
		} finally {
			if (fis != null) {
				fis.close();
			}
			if (fr != null) {
				fr.close();
			}
		}
	}

	protected void loadConfig1() throws UnknownHostException {

		_hideIps = _properties.getProperty("hideips", "").equalsIgnoreCase(
				"true");

		StringTokenizer st = new StringTokenizer(_properties.getProperty(
				"bouncer_ip", ""), " ");

		ArrayList<InetAddress> bouncerIps = new ArrayList<InetAddress>();

		while (st.hasMoreTokens()) {
			bouncerIps.add(InetAddress.getByName(st.nextToken()));
		}

		_bouncerIps = bouncerIps;
		
		ArrayList<String> cipherSuites = new ArrayList<String>();
		for (int x = 1;;x++) {
			String cipherSuite = _properties.getProperty("cipher." + x);
			if (cipherSuite != null) {
				cipherSuites.add(cipherSuite);
			} else {
				break;
			}
		}
		if (cipherSuites.size() == 0) {
			_cipherSuites = null;
		} else {
			_cipherSuites = new String[cipherSuites.size()];
			for (int x = 0; x<_cipherSuites.length; x++) {
				_cipherSuites[x] = cipherSuites.get(x);
			}
		}
	}

	public void loadReaderForTest(Reader in) throws IOException {
		loadConfig2(in);
	}

	protected void loadConfig2(Reader in2) throws IOException {
		LineNumberReader in = new LineNumberReader(in2);

		String line;

		while ((line = in.readLine()) != null) {
			StringTokenizer st = new StringTokenizer(line);

			if (!st.hasMoreTokens()) {
				continue;
			}

			String cmd = st.nextToken();

			try {
				// login_prompt <string>
				if (cmd.equals("login_prompt")) {
					_loginPrompt = line.substring(13);
				}
				// max_users <maxUsersTotal> <maxUsersExempt>
				else if (cmd.equals("max_users")) {
					_maxUsersTotal = Integer.parseInt(st.nextToken());
					_maxUsersExempt = Integer.parseInt(st.nextToken());
				} else if (cmd.equals("pasv_addr")) {
					_pasv_addr = st.nextToken();
				} else if (cmd.equals("pasv_ports")) {
					String[] temp = st.nextToken().split("-");
					_portRange = new PortRange(Integer.parseInt(temp[0]),
							Integer.parseInt(temp[1]));
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
				// msgpath <path> <filename> <flag/=group/-user>
				else if (cmd.equals("msgpath")) {
					DirectoryHandle path = new DirectoryHandle(st.nextToken());
					String messageFile = st.nextToken();
					_msgpath.add(new MessagePathPermission(path, messageFile,
							makeUsers(st)));
				}
				// creditloss <path> <multiplier> [<-user|=group|flag> ...]
				else if (cmd.equals("creditloss")) {
					makeRatioPermission(_creditloss, st);
				}
				// creditcheck <path> <ratio> [<-user|=group|flag> ...]
				else if (cmd.equals("creditcheck")) {
					makeRatioPermission(_creditcheck, st);
				} else if (cmd.equals("pathperm")) {
					addGlobPathPermission(st.nextToken(), st);

				} else if (cmd.equals("privpath") || cmd.equals("dirlog")
						|| cmd.equals("hideinwho") || cmd.equals("makedir")
						|| cmd.equals("pre") || cmd.equals("upload")
						|| cmd.equals("download") || cmd.equals("delete")
						|| cmd.equals("deleteown") || cmd.equals("rename")
						|| cmd.equals("nostatsup") || cmd.equals("nostatsdn")
						|| cmd.equals("renameown") || cmd.equals("request")) {
					addGlobPathPermission(cmd, st);
				} else if (cmd.equals("makedir2")) {
					addPatternPathPermission(
							cmd.substring(0, cmd.length() - 1), st);
				} else if ("userrejectsecure".equals(cmd)
						|| "userrejectinsecure".equals(cmd)
						|| "denydiruncrypted".equals(cmd)
						|| "denydatauncrypted".equals(cmd)
						|| "give".equals(cmd) || "take".equals(cmd)) {
					if (_permissions.containsKey(cmd)) {
						throw new RuntimeException(
								"Duplicate key in perms.conf: " + cmd
										+ " line: " + in.getLineNumber());
					}

					_permissions.put(cmd, new Permission(makeUsers(st)));
				} else if ("shutdown".equals(cmd)) {
					_shutdown = new Permission(makeUsers(st));
				} else {
					if (!cmd.startsWith("#")) {
						addGlobPathPermission(cmd, st);
					}
				}
			} catch (Exception e) {
				logger.warn("Exception when reading " + newConf + " line "
						+ in.getLineNumber(), e);
			}
		}

		// notify any observers so they can add their own permissions
		notifyObservers();
		if (_portRange == null) {
			// default portrange if none specified
			_portRange = new PortRange();
		}
	}

	private void addGlobPathPermission(String key, StringTokenizer st)
			throws MalformedPatternException {
		addPathPermission(key, new GlobPathPermission(new GlobCompiler()
				.compile(st.nextToken()), makeUsers(st)));
	}

	private void addPatternPathPermission(String key, StringTokenizer st) {
		addPathPermission(key, new PatternPathPermission(st.nextToken(),
				makeUsers(st)));
	}

	public void addPathPermission(String key, PathPermission permission) {
		ArrayList<PathPermission> perms = _pathsPerms.get(key);

		if (perms == null) {
			perms = new ArrayList<PathPermission>();
			_pathsPerms.put(key, perms);
		}
		perms.add(permission);
	}

	private static void replaceChars(StringBuffer source, Character oldChar,
			Character newChar) {
		if (newChar == null) {
			int x = 0;

			while (x < source.length()) {
				if (source.charAt(x) == oldChar.charValue()) {
					source.deleteCharAt(x);
				} else {
					x++;
				}
			}
		} else {
			int x = 0;

			while (x < source.length()) {
				if (source.charAt(x) == oldChar.charValue()) {
					source.setCharAt(x, newChar.charValue());
				}

				x++;
			}
		}
	}

	private static String replaceName(String source, StringTokenizer st) {
		StringBuffer sb = new StringBuffer(source);
		Character oldChar = null;
		Character newChar = null;

		while (true) {
			if (!st.hasMoreTokens()) {
				return sb.toString();
			}

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

	/**
	 * Returns true if user is allowed into a shutdown server.
	 */
	public boolean isLoginAllowed(User user) {
		return (_shutdown == null) ? true : _shutdown.check(user);
	}

	public PortRange getPortRange() {
		return _portRange;
	}

	public static void reload() {
		_config = new FtpConfig();
	}
}
