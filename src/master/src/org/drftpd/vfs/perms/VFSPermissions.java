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

import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.drftpd.permissions.PathPermission;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

/**
 * This object handles all the permissions releated to the VFS.
 * @author fr0w
 * @version $Id$
 */
public class VFSPermissions {
	private final static Logger logger = Logger.getLogger(VFSPermissions.class);

	private HashMap<String, PermissionWrapper> _handlersMap;	
	
	// HashMap<Type, HashMap<Directive, List<PathPermission>>> 
	private HashMap<String, HashMap<String, LinkedList<PathPermission>>> _pathPerms;	
	private HashMap<String, String> _directiveToType;
	private HashMap<String, TreeMap<Integer, String>> _priorities;
	
	public VFSPermissions() {
		loadExtensions();
		
		_pathPerms = new HashMap<String, HashMap<String, LinkedList<PathPermission>>>();
	}

	public void loadExtensions() {
		_handlersMap = new HashMap<String, PermissionWrapper>();
		_directiveToType = new HashMap<String, String>();
		_priorities = new HashMap<String, TreeMap<Integer, String>>();

		/*
		<extension-point id="VFSPerm">
	      	<parameter-def id="Class" />
	    	<parameter-def id="Method" />
	    	<parameter-def id="Type" />
	     	<parameter-def id="Directive" />
	     	<parameter-def id="Priority" />
	   	</extension-point>
		*/

		try {
			List<PluginObjectContainer<VFSPermHandler>> loadedHandlers =
				CommonPluginUtils.getPluginObjectsInContainer(this, "master", "VFSPerm", "Class", "Method",
						new Class[] { String.class, StringTokenizer.class });
			for (PluginObjectContainer<VFSPermHandler> container : loadedHandlers) {
				String directive = container.getPluginExtension().getParameter("Directive").valueAsString();

				if (_handlersMap.containsKey(directive)) {
					logger.debug("A handler for '"+ directive +"' already loaded, check your plugin.xml's");
					continue;
				}

				String type = container.getPluginExtension().getParameter("Type").valueAsString().toLowerCase();

				if (!verifyType(type)) {
					logger.debug("Invalid VFS permission type ("+type+") for directive '"+directive+"'.");
					continue;
				}
				PermissionWrapper pw = new PermissionWrapper(container.getPluginObject(), container.getPluginMethod());				
				_handlersMap.put(directive, pw);
				_directiveToType.put(directive, type);
				
				// building execution order.
				int priority = container.getPluginExtension().getParameter("Priority").valueAsNumber().intValue();
				TreeMap<Integer, String> order = _priorities.get(type);
				if (order == null) {
					order = new TreeMap<Integer, String>();
					_priorities.put(type, order);
				}
				while (true) {
					if (order.containsKey(priority)) {
						logger.debug("The slot that " + directive + " is trying to use is already allocated, " +
								"check the xmls, allocating the next available slot");
						priority++;
					} else {
						order.put(priority, directive);
						break;
					}
				}
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for master extension point 'VFSPerm', possibly the master"
					+" extension point definition has changed in the plugin.xml",e);
		}
	}
	
	private boolean verifyType(String type) {
		type = type.toLowerCase();
		if (type.equals("upload") || type.equals("makedir") || type.equals("delete") 
				|| type.equals("deleteown")	|| type.startsWith("rename") || type.equals("renameown")
				|| type.equals("privpath") || type.equals("download")) {
			return true;
		}
		
		return false;
	}
	
	public void handleLine(String directive, StringTokenizer st) {
		if (!_handlersMap.containsKey(directive)) {
			throw new UnsupportedOperationException("No VFSPermHandler found for this directive: "+ directive);
		}
		
		PermissionWrapper pw = _handlersMap.get(directive);
		
		pw.handle(directive, st);
	}
	
	protected void addPermissionToMap(String directive, PathPermission pathPerm) {
		String type = _directiveToType.get(directive);
		
		HashMap<String, LinkedList<PathPermission>> map = _pathPerms.get(type);
		
		if (map == null) {
			map = new HashMap<String, LinkedList<PathPermission>>();
			_pathPerms.put(type, map);
		}
		
		LinkedList<PathPermission> list;
		if (!map.containsKey(directive)) {
			list = new LinkedList<PathPermission>();
			map.put(directive, list);
		} else {
			list = map.get(directive);
		}
		
		list.add(pathPerm);
	}
	
	public boolean checkPathPermission(String type, User user, String path) {
		return checkPathPermission(type, user, path, false);
	}

	public boolean checkPathPermission(String type, User user, String path, boolean defaults) {
		return checkPathPermission(type, user, path, defaults, false);
	}
	
	public boolean checkPathPermission(String type, User user, String path, boolean defaults, boolean invertUserSemantic) {
		
		if (!verifyType(type)) {
			throw new IllegalArgumentException("Invalid VFS perm type.");
		}
		
		HashMap<String, LinkedList<PathPermission>> map = _pathPerms.get(type);
		TreeMap<Integer, String> order = _priorities.get(type);
		
		if (map == null) {
			return defaults;
		}
		
		if (order == null) {
			NullPointerException npe = new NullPointerException("You've got some screwy plugin.xml files!  Blame fr0w!");
			logger.error(npe, npe);
			throw npe;
		}
				
		for (Entry<Integer, String> entry : order.entrySet()) {
			String directive = entry.getValue();
			
			LinkedList<PathPermission> perms = map.get(directive);
			
			if (perms == null) {
				// 'directive' was not found in perms.conf
				continue;
			}
			
			if (perms != null && !perms.isEmpty()) {
				for (PathPermission perm : perms) {
					if (perm.checkPath(path)) {
						if (invertUserSemantic) {
							return !perm.check(user);
						} else {
							return perm.check(user);
						}
					}
				}
			}
		}
		
		return defaults;
	}
	
	// if you want to debug this class, call this method.
	public void dumpMap() {
		for (Entry<String, HashMap<String, LinkedList<PathPermission>>> e1 : _pathPerms.entrySet()) {
			String type = e1.getKey();
			HashMap<String, LinkedList<PathPermission>> map = e1.getValue();
			
			logger.debug(type + " is handling:");
			TreeMap<Integer, String> order = _priorities.get(type);
			for (Iterator<Entry<Integer, String>> iter = order.entrySet().iterator(); iter.hasNext();) {
				Entry<Integer, String> e2 = iter.next();
				String directive = e2.getValue();
				logger.debug(e2.getKey()+". "+ directive);
				if (map.get(directive) == null) {
					// 'directive' was not found in perms.conf
					continue;
				}
				logger.debug(map.get(directive).toString());
			}
		}
	}
}
