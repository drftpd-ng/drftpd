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
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyedMap;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.permissions.PathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.slave.Slave;
import org.drftpd.usermanager.User;
import org.drftpd.util.PortRange;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.perms.VFSPermissions;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * Handles the loading of 'drftpd.conf' and 'conf/perms.conf'<br>
 * The directives that are going to be handled by this class are loaded during
 * the startup process and *MUST* be an extension of the master extension-point "ConfigHandler".<br>
 * No hard coding is needed to handle new directives, simply create a new extension.
 * @author fr0w
 * @version $Id$
 */
public class ConfigManager implements ConfigInterface {
	private static final Logger logger = Logger.getLogger(ConfigManager.class);
	private static final File permsFile = new File("conf/perms.conf");
	private static final File mainFile = new File("drftpd.conf");
	
	private static final Key PATHPERMS = new Key(ConfigManager.class, "pathPerms", Hashtable.class);
	private static final Key PERMS = new Key(ConfigManager.class, "perms", Hashtable.class);

	private HashMap<String, ConfigContainer> _directivesMap;
	private KeyedMap<Key, Object> _keyedMap;
	private Properties _mainCfg;
	
	// private Hashtable<String, ArrayList<PathPermission>> _pathPerms;
	// private Hashtable<String, Permission> _permissions;
	
	private VFSPermissions _vfsPerms;
	
	private ArrayList<InetAddress> _bouncerIps;
	private String _loginPrompt = Slave.VERSION + " http://drftpd.org";
	private String _pasvAddr;
	private PortRange _portRange = new PortRange(0); 
	private boolean _hideIps = true;
	
	private int _maxUsersTotal = Integer.MAX_VALUE;
	private int _maxUsersExempt = 0;
	
	/**
	 * Reload all VFSPermHandlers and ConfigHandlers.
	 * Also re-read the config files.
	 */
	public void reload() {
		loadVFSPermissions();
		loadConfigHandlers();
		loadMainProperties();
		
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
	public KeyedMap<Key, Object> getKeyedMap() {
		return _keyedMap;
	}
	
	/**
	 * Load all connected handlers.
	 */
	private void loadConfigHandlers() {
		_directivesMap = new HashMap<String, ConfigContainer>();
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint exp = manager.getRegistry().getExtensionPoint("master", "ConfigHandler");
		
		/*   
		<extension-point id="ConfigHandler">
		    <parameter-def id="Class" />
		    <parameter-def id="Method" />
		 	<parameter-def id="Directive" />
		 </extension-point>
		 */
		
		for (Extension ext : exp.getConnectedExtensions()) {
			try {
				String directive = ext.getParameter("Directive").valueAsString();
				
				if (_directivesMap.containsKey(directive)) {
					logger.debug("A handler for "+ directive +" already loaded, check your plugin.xml's");
					continue;
				}
				
				manager.activatePlugin(ext.getDeclaringPluginDescriptor().getId());
				
				ClassLoader clsLoader = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());				
				Class<?> clazz = clsLoader.loadClass(ext.getParameter("Class").valueAsString());				
				ConfigHandler cfgHnd = (ConfigHandler) clazz.newInstance();
				Method m = clazz.getMethod(ext.getParameter("Method").valueAsString(), new Class[] { String.class, StringTokenizer.class });
				
				ConfigContainer cc = new ConfigContainer(cfgHnd, m);				
				_directivesMap.put(directive, cc);
			} catch (Exception e) {
				logger.error("Impossible to load extension: "+ ext.getId() ,e);
			}
		}
	}
	
	/**
	 * Reads 'drftpd.conf' and save it to a Properties object.
	 * @see #getMainProperties()
	 */
	private void loadMainProperties() {
		FileInputStream is = null;
		try {
			_mainCfg = new Properties();
			is = new FileInputStream(mainFile);
			_mainCfg.load(is);
		} catch (IOException e) {
			logger.error("Unable to read drftpd.conf", e);
		} finally {
			if (is != null) {
				try { is.close(); }
				catch (IOException e) { }
			}
		}
	}
	
	/**
	 * Initializes the KeyedMap.
	 * @see #getKeyedMap()
	 */
	private void initializeKeyedMap() {
		_keyedMap = new KeyedMap<Key, Object>();
		
		_keyedMap.setObject(PATHPERMS, new Hashtable<String, ArrayList<PathPermission>>());
		_keyedMap.setObject(PERMS, new Hashtable<String, Permission>());
	}
	
	@SuppressWarnings("unchecked")
	private Hashtable<String, ArrayList<PathPermission>> getPathPermsMap() {
		return (Hashtable<String, ArrayList<PathPermission>>) _keyedMap.getObject(PATHPERMS, null);
	}
	
	@SuppressWarnings("unchecked")
	private Hashtable<String, Permission> getPermissionsMap() {
		return (Hashtable<String, Permission>) _keyedMap.getObject(PERMS, null);
	}
	
	/**
	 * @return a Properties object containing all data loaded from 'drftpd.conf'.
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
				} else if (drct.equals("bouncer_ips")) {
					ArrayList<InetAddress> ips = new ArrayList<InetAddress>();
					while (st.hasMoreTokens()) {
						ips.add(InetAddress.getByName(st.nextToken()));
					}
					_bouncerIps = ips;
				} // TODO cipher suites.
				else {
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

	public float getCreditCheckRatio(DirectoryHandle path, User fromUser) {
		// TODO Auto-generated method stub
		return 0;
	}

	public float getCreditLossRatio(DirectoryHandle path, User fromUser) {
		// TODO Auto-generated method stub
		return 0;
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
		if (_pasvAddr == null) throw new NullPointerException("pasv_addr not configured");
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

	public String[] getCipherSuites() {
		// TODO Auto-generated method stub
		return null;
	}
}