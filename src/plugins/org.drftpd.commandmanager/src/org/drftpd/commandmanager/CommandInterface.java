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
package org.drftpd.commandmanager;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

/**
 * @author djb61
 * @version $Id$
 */
public abstract class CommandInterface {

	private static final Logger logger = Logger.getLogger(CommandInterface.class);

	protected String[] _featReplies;

	private Map<Integer, HookContainer<PostHookInterface>> _postHooks;

	private Map<Integer, HookContainer<PreHookInterface>> _preHooks;

	public CommandInterface() {
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		_preHooks = Collections.synchronizedMap(new TreeMap<Integer, HookContainer<PreHookInterface>>());
		_postHooks = Collections.synchronizedMap(new TreeMap<Integer, HookContainer<PostHookInterface>>());

		// Populate all available pre hooks
		try {
			List<PluginObjectContainer<PreHookInterface>> loadedPreHooks = 
				CommonPluginUtils.getPluginObjectsInContainer(this, pluginName, "PreHook", "HookClass", "HookMethod",
						"ParentMethod", method, new Class[] {CommandRequest.class});
			for (PluginObjectContainer<PreHookInterface> container : loadedPreHooks) {
				int priority = container.getPluginExtension().getParameter("Priority").valueAsNumber().intValue();
				if (_preHooks.containsKey(priority)) {
					logger.warn(pluginName + " already has a pre hook with priority " +
							priority + " adding " + container.getPluginExtension().getId() + " with next available priority");
					while (_preHooks.containsKey(priority)) {
						priority++;
					}
				}
				PreHookInterface preHookInstance = container.getPluginObject();
				preHookInstance.initialize(cManager);
				_preHooks.put(priority,
						new HookContainer<PreHookInterface>(container.getPluginMethod(),preHookInstance));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for "+pluginName+" extension point 'PreHook', possibly the "+pluginName
					+" extension point definition has changed in the plugin.xml",e);
		}

		// Populate all available post hooks
		try {
			List<PluginObjectContainer<PostHookInterface>> loadedPostHooks = 
				CommonPluginUtils.getPluginObjectsInContainer(this, pluginName, "PostHook", "HookClass", "HookMethod",
						"ParentMethod", method, new Class[] {CommandRequest.class, CommandResponse.class});
			for (PluginObjectContainer<PostHookInterface> container : loadedPostHooks) {
				int priority = container.getPluginExtension().getParameter("Priority").valueAsNumber().intValue();
				if (_postHooks.containsKey(priority)) {
					logger.warn(pluginName + " already has a post hook with priority " +
							priority + " adding " + container.getPluginExtension().getId() + " with next available priority");
					while (_postHooks.containsKey(priority)) {
						priority++;
					}
				}
				PostHookInterface postHookInstance = container.getPluginObject();
				postHookInstance.initialize(cManager);
				_postHooks.put(priority,
						new HookContainer<PostHookInterface>(container.getPluginMethod(),postHookInstance));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for "+pluginName+" extension point 'PostHook', possibly the "+pluginName
					+" extension point definition has changed in the plugin.xml",e);
		}
	}

	protected void doPostHooks(CommandRequestInterface request, CommandResponseInterface response) {
		for (HookContainer<PostHookInterface> hook : _postHooks.values()) {
			Method m = hook.getMethod();
			try {
				m.invoke(hook.getHookInterfaceInstance(), new Object[] {request, response});
			}
			catch (Exception e) {
				logger.error("Error while loading/invoking posthook " + m.toString(), e);
				/* Not that important, this just means that this post hook
				 * failed and we'll just move onto the next one
				 */
			}
		}
	}

	protected CommandRequestInterface doPreHooks(CommandRequestInterface request) {
		request.setAllowed(true);
		for (HookContainer<PreHookInterface> hook : _preHooks.values()) {
			Method m = hook.getMethod();
			try {
				request = (CommandRequestInterface) m.invoke(hook.getHookInterfaceInstance(), new Object[] {request});
			}
			catch (Exception e) {
				logger.error("Error while loading/invoking prehook " + m.toString(), e);
				/* Not that important, this just means that this pre hook
				 * failed and we'll just move onto the next one
				 */
			}
		}
		return request;
	}

	protected User getUserObject(String user) throws NoSuchUserException, UserFileException {
		return GlobalContext.getGlobalContext().getUserManager().getUserByName(user);
	}

	public String[] getFeatReplies() {
		return _featReplies;
	}

	public void addTextToResponse(CommandResponse response, String file)
	throws FileNotFoundException, IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), "ISO-8859-1"));
			response.addComment(reader);
			reader.close();
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	@EventSubscriber
	public void onUnloadPluginEvent(UnloadPluginEvent event) {
		String currentPlugin = CommonPluginUtils.getPluginIdForObject(this);
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String plugin = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (plugin.equals(currentPlugin) && extension.equals("PostHook")) {
				for (Iterator<Entry<Integer, HookContainer<PostHookInterface>>> iter = _postHooks.entrySet().iterator(); iter.hasNext();) {
					Entry<Integer, HookContainer<PostHookInterface>> entry = iter.next();
					if (CommonPluginUtils.getPluginIdForObject(entry.getValue().getHookInterfaceInstance()).equals(event.getPlugin())) {
						logger.debug("Removing post hook provided by " + event.getPlugin() + " from " + currentPlugin);
						iter.remove();
					}
				}
			}
			if (plugin.equals(currentPlugin) && extension.equals("PreHook")) {
				for (Iterator<Entry<Integer, HookContainer<PreHookInterface>>> iter = _preHooks.entrySet().iterator(); iter.hasNext();) {
					Entry<Integer, HookContainer<PreHookInterface>> entry = iter.next();
					if (CommonPluginUtils.getPluginIdForObject(entry.getValue().getHookInterfaceInstance()).equals(event.getPlugin())) {
						logger.debug("Removing pre hook provided by " + event.getPlugin() + " from " + currentPlugin);
						iter.remove();
					}
				}
			}
		}
	}

	protected boolean checkCustomPermissionWithPrimaryGroup(User targetUser, CommandRequest request, String permissionName, String defaultPermission) {
		if (checkCustomPermission(request, permissionName, defaultPermission)) {
			return false;
		}
		try {
			return targetUser.getGroup().equals(request.getUserObject().getGroup());
		} catch (NoSuchUserException e) {
			logger.warn("",e);
			return false;
		} catch (UserFileException e) {
			logger.warn("",e);
			return false;
		}		
	}

	protected boolean checkCustomPermission(CommandRequest request, String permissionName,
			String defaultPermission) {
		String permissionString = request.getProperties().getProperty(permissionName,defaultPermission);
		User user;
		try {
			user = request.getUserObject();
		} catch (NoSuchUserException e) {
			logger.warn("",e);
			return false;
		} catch (UserFileException e) {
			logger.warn("",e);
			return false;
		}
		return new Permission(permissionString).check(user);
	}
}
