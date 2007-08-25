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
package org.drftpd.vfs.perms;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.drftpd.permissions.PathPermission;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * This object handles all the permissions releated to the VFS.
 * @author fr0w
 * @version $Id$
 */
public class VFSPermissions {
	private final static Logger logger = Logger.getLogger(VFSPermissions.class);

	private HashMap<String, PermissionWtapper> _handlersMap;	
	private Hashtable<String, ArrayList<PathPermission>> _pathPerms;
	
	public VFSPermissions() {
		loadExtensions();
		
		_pathPerms = new Hashtable<String, ArrayList<PathPermission>>();
	}

	public void loadExtensions() {
		_handlersMap = new HashMap<String, PermissionWtapper>();
		
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint exp = manager.getRegistry().getExtensionPoint("master", "VFSPerm");
		
		/*   
		<extension-point id="VFSPerm">
	      	<parameter-def id="Class" />
	    	<parameter-def id="Method" />
	    	<parameter-def id="Type" />
	     	<parameter-def id="Directive" />
	   	</extension-point>
		*/
		
		for (Extension ext : exp.getConnectedExtensions()) {
			try {
				String directive = ext.getParameter("Directive").valueAsString();
				
				if (_handlersMap.containsKey(directive)) {
					logger.debug("A handler for "+ directive +" already loaded, check your plugin.xml's");
					continue;
				}
				
				manager.activatePlugin(ext.getDeclaringPluginDescriptor().getId());
				
				ClassLoader clsLoader = manager.getPluginClassLoader(ext.getDeclaringPluginDescriptor());				
				Class<?> clazz = clsLoader.loadClass(ext.getParameter("Class").valueAsString());				
				Method m = clazz.getMethod(ext.getParameter("Method").valueAsString(), new Class[] { String.class, StringTokenizer.class });
				
				String type = ext.getParameter("Type").valueAsString().toLowerCase();
				
				if (!verifyType(type)) {
					throw new IllegalArgumentException("Invalid VFS perm type.");
				}

				VFSPermHandler permHnd = (VFSPermHandler) clazz.newInstance();
				PermissionWtapper pw = new PermissionWtapper(permHnd, m);				
				_handlersMap.put(directive, pw);
			} catch (Exception e) {
				logger.error(e, e);
			}
		}
	}
	
	private boolean verifyType(String type) {
		type = type.toLowerCase();
		if (type.equals("upload") || type.equals("makedir") || type.startsWith("delete")
				|| type.startsWith("rename") || type.equals("privpath") || type.equals("download")) {
			return true;
		}
		
		return false;
	}
	
	public void handleLine(String directive, StringTokenizer st) {
		if (!_handlersMap.containsKey(directive)) {
			throw new UnsupportedOperationException("No VFSPermHandler found for this directive: "+ directive);
		}
		
		PermissionWtapper pw = _handlersMap.get(directive);
		
		try {
			pw.getMethod().invoke(pw.getVFSPermHandler(), directive, st);
		} catch (Exception e) {
			logger.warn(e, e);
		}	
	}
	
	protected void addPermissionToMap(String directive, PathPermission pathPerm) {
		ArrayList<PathPermission> list;
		if (!_pathPerms.containsKey(directive)) {
			list = new ArrayList<PathPermission>();
			_pathPerms.put(directive, list);
		} else {
			list = _pathPerms.get(directive);
		}
		
		list.add(pathPerm);
	}
	
	public boolean checkPathPermission(String type, User user, DirectoryHandle path) {
		return checkPathPermission(type, user, path, false);
	}
	
	public boolean checkPathPermission(String type, User user, DirectoryHandle path, boolean defaults) {
		logger.debug("type = "+ type);
		
		if (!verifyType(type)) {
			throw new IllegalArgumentException("Invalid VFS perm type.");
		}
		
		ArrayList<PathPermission> perms = _pathPerms.get(type);
		
		if (perms != null && !perms.isEmpty()) {
			for (PathPermission perm : perms) {
				if (perm.checkPath(path)) {
					return perm.check(user);
				}
			}
		}
		
		return defaults;
	}
}
