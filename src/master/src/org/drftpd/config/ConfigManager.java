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
package org.drftpd.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.perms.VFSPermissions;

/**
 * Handles the loading of 'master.conf' and 'conf/perms.conf'<br>
 * The directives that are going to be handled by this class are loaded during
 * the startup process and *MUST* be an extension of the master extension-point "ConfigHandler".<br>
 * No hard coding is needed to handle new directives, simply create a new extension.
 * @author fr0w
 * @version $Id$
 */
public class ConfigManager implements ConfigInterface {
	private static final Logger logger = Logger.getLogger(ConfigManager.class);
	private static final File permsFile = new File("conf/perms.conf");
	private static final File mainFile = new File("master.conf");
	
	private static final Key<Hashtable<String, ArrayList<PathPermission>>> PATHPERMS 
							= new Key<Hashtable<String, ArrayList<PathPermission>>>(ConfigManager.class, "pathPerms");
	private static final Key<Hashtable<String, Permission>> PERMS 
							= new Key<Hashtable<String, Permission>>(ConfigManager.class, "perms");

	private HashMap<String, ConfigContainer> _directivesMap;
	private KeyedMap<Key<?>, Object> _keyedMap;
	private Properties _mainCfg;
	
	private VFSPermissions _vfsPerms;
	
	private ArrayList<InetAddress> _bouncerIps;
	private String _loginPrompt = GlobalContext.VERSION + " http://drftpd.org";
	private String _pasvAddr = null;
	private PortRange _portRange = new PortRange(0); 
	private boolean _hideIps = true;
	
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private int _maxUsersExempt = 0;
	
	private String[] _cipherSuites = null;
	
	/**
	 * Reload all VFSPermHandlers and ConfigHandlers.
	 * Also re-read the config files.
	 */
	public void reload() {
		loadVFSPermissions();
		loadConfigHandlers();
		loadMainProperties();
		parseCipherSuites();
		
		initializeKeyedMap();
		
		readConf();
	}
	
	private void loadVFSPermissions() {
		_vfsPerms = new VFSPermissions();
	}
	
	/**
	 * @return the VFSPermission object.
	 * @see VFSPermissions
	 */
	public VFSPermissions getVFSPermissions() {
		return _vfsPerms;
	}
	
	/**
	 * Returns the KeyedMap that allow dynamic and persistent storage of the loaded permissions.<br>
	 * For a better understanding see how does the 'msgpath' directive works.
	 * @see DefaultConfigHandler 
	 * @see DefaultConfigPostHook
	 */
	public KeyedMap<Key<?>, Object> getKeyedMap() {
		return _keyedMap;
	}
	
	/**
	 * Load all connected handlers.
	 */
	private void loadConfigHandlers() {
		_directivesMap = new HashMap<String, ConfigContainer>();

		try {
			List<PluginObjectContainer<ConfigHandler>> loadedDirectives = 
				CommonPluginUtils.getPluginObjectsInContainer(this, "master", "ConfigHandler", "Class", "Method",
						new Class[] { String.class, StringTokenizer.class });
			for (PluginObjectContainer<ConfigHandler> container : loadedDirectives) {
				String directive = container.getPluginExtension().getParameter("Directive").valueAsString();
				if (_directivesMap.containsKey(directive)) {
					logger.debug("A handler for "+ directive +" already loaded, check your plugin.xml's");
					continue;
				}
				ConfigContainer cc = new ConfigContainer(container.getPluginObject(), container.getPluginMethod());				
				_directivesMap.put(directive, cc);
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for master extension point 'Directive', possibly the master"
					+" extension point definition has changed in the plugin.xml",e);
		}
	}
	
	/**
	 * Reads 'master.conf' and save it in a Properties object.
	 * @see #getMainProperties()
	 */
	private void loadMainProperties() {
		FileInputStream is = null;
		try {
			_mainCfg = new Properties();
			is = new FileInputStream(mainFile);
			_mainCfg.load(is);
		} catch (IOException e) {
			logger.error("Unable to read "+mainFile.getPath(), e);
		} finally {
			if (is != null) {
				try { is.close(); }
				catch (IOException e) { }
			}
		}
	}
	
	private void parseCipherSuites() {
		ArrayList<String> cipherSuites = new ArrayList<String>();
		for (int x = 1;; x++) {
			String cipherSuite = _mainCfg.getProperty("cipher." + x);
			if (cipherSuite != null) {
				cipherSuites.add(cipherSuite);
			} else {
				break;
			}
		}
		if (cipherSuites.size() == 0) {
			_cipherSuites = null;
		} else {
			_cipherSuites = cipherSuites.toArray(new String[cipherSuites.size()]);
		}
	}
	
	/**
	 * Initializes the KeyedMap.
	 * @see #getKeyedMap()
	 */
	private void initializeKeyedMap() {
		_keyedMap = new KeyedMap<Key<?>, Object>();
		
		_keyedMap.setObject(PATHPERMS, new Hashtable<String, ArrayList<PathPermission>>());
		_keyedMap.setObject(PERMS, new Hashtable<String, Permission>());
	}
	
	private Hashtable<String, ArrayList<PathPermission>> getPathPermsMap() {
		return _keyedMap.getObject(PATHPERMS, null);
	}
	
	private Hashtable<String, Permission> getPermissionsMap() {
		return _keyedMap.getObject(PERMS, null);
	}
	
	/**
	 * @return a Properties object containing all data loaded from 'master.conf'.
	 */
	public Properties getMainProperties() {
		return _mainCfg;
	}
	
	/**
	 * Reads 'conf/perms.conf' handling what can be handled, ignoring what's does not have an available handler.
	 */
	private void readConf() {
		LineNumberReader in = null;
		try {
			in = new LineNumberReader(new FileReader(permsFile));
			String line;
			
			while ((line = in.readLine()) != null) {				
				StringTokenizer st = new StringTokenizer(line);
				
				if (line.startsWith("#") || !st.hasMoreTokens()) {
					continue;
				}
				
				String drct = st.nextToken().toLowerCase();
				
				/*
				 * Built-in directives.
				 */
		
				if (drct.equals("login_prompt")) {
					_loginPrompt = line.substring("login_prompt".length()).trim();
				} else if (drct.equals("max_users")) {
					_maxUsersTotal = Integer.parseInt(st.nextToken());
					_maxUsersExempt = Integer.parseInt(st.nextToken());
				} else if (drct.equals("pasv_addr")) {
					_pasvAddr = st.nextToken();
				} else if (drct.equals("pasv_ports")) {
					String[] temp = st.nextToken().split("-");
					_portRange = new PortRange(Integer.parseInt(temp[0]), Integer.parseInt(temp[1]), 0);
				} else if (drct.equals("hide_ips")) {
					_hideIps = st.nextToken().equalsIgnoreCase("true") ? true : false;
				} else if (drct.equals("allow_connections")) {
					getPermissionsMap().put("allow_connections", new Permission(Permission.makeUsers(st)));
				} else if (drct.equals("exempt")) {
					getPermissionsMap().put("exempt", new Permission(Permission.makeUsers(st)));
				} else if (drct.equals("bouncer_ips")) {
					ArrayList<InetAddress> ips = new ArrayList<InetAddress>();
					while (st.hasMoreTokens()) {
						ips.add(InetAddress.getByName(st.nextToken()));
					}
					_bouncerIps = ips;
				} else {
					handleLine(drct, st);
				}			
			}
		} catch (IOException e) {
			logger.info("Unable to parse "+permsFile.getName(), e);
		} finally {
			if (in != null) {
				try { in.close(); }
				catch (IOException e) { }
			}
		}
	}
	
	private void handleLine(String directive, StringTokenizer st) {
		try {
			getVFSPermissions().handleLine(directive, st);
			return; // successfully handled by VFSPermissions, stop!
		} catch (UnsupportedOperationException e) {
			// could not be handled by VFSPermissions, let's try the other ones!
		}
		
		ConfigContainer cc = _directivesMap.get(directive);
		
		if (cc == null) {
			logger.debug("No handler found for '"+ directive+"' ignoring line");
			return;
		}
		
		try {
			cc.getMethod().invoke(cc.getInstance(), directive, st);
		} catch (Exception e) {
			logger.debug("Error while handling directive: "+directive, e);
		}
	}

	public void addPathPermission(String directive, PathPermission perm) {
		ArrayList<PathPermission> list;
		if (!getPathPermsMap().containsKey(directive)) {
			list = new ArrayList<PathPermission>();
			getPathPermsMap().put(directive, list);
		} else {
			list = getPathPermsMap().get(directive);
		}
		
		list.add(perm);
	}


	public boolean checkPathPermission(String directive, User user, DirectoryHandle path) {
		return checkPathPermission(directive, user, path, false);
	}


	public boolean checkPathPermission(String directive, User user, 
			DirectoryHandle path, boolean defaults) {
		ArrayList<PathPermission> perms = getPathPermsMap().get(directive);
		
		if (perms != null && !perms.isEmpty()) {
			for (PathPermission perm : perms) {
				if (perm.checkPath(path)) {
					return perm.check(user);
				}
			}
		}
		
		return defaults;
	}

	public void addPermission(String directive, Permission permission) {
		boolean alreadyExists = getPermissionsMap().containsKey(directive);
		
		if (alreadyExists) {
			// TODO what's best replace the existing one or keep it? 
			logger.info("The directive '"+directive+"' is already on the permission map, check out your "+permsFile.getName());
			return;
		}
		
		getPermissionsMap().put(directive, permission);
	}
	
	public boolean checkPermission(String key, User user) {
		Permission perm = getPermissionsMap().get(key);
		return (perm == null) ? false : perm.check(user);
	}

	public List<InetAddress> getBouncerIps() {
		return _bouncerIps;
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
		if (_pasvAddr == null) 
			throw new NullPointerException("pasv_addr not configured");
		return _pasvAddr;
	}

	public PortRange getPortRange() {
		return _portRange;
	}
	
	public boolean isLoginAllowed(User user) {
		Permission perm = getPermissionsMap().get("allow_connections");
		
		if (perm == null) {
			return true;
		} else {
			return perm.check(user);
		}
	}

	public boolean isLoginExempt(User user) {
		Permission perm = getPermissionsMap().get("exempt");
		
		if (perm == null) {
			return true;
		} else {
			return perm.check(user);
		}
	}

	public String[] getCipherSuites() {
		return _cipherSuites;
	}
}
