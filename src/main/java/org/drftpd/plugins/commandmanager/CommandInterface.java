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
package org.drftpd.plugins.commandmanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commandmanager.CommandRequestInterface;
import org.drftpd.master.commandmanager.CommandResponseInterface;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.io.*;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author djb61
 * @version $Id$
 */
public abstract class CommandInterface {

	private static final Logger logger = LogManager.getLogger(CommandInterface.class);

	protected String[] _featReplies;

	private Map<Integer, HookContainer<PostHookInterface>> _postHooks;

	private Map<Integer, HookContainer<PreHookInterface>> _preHooks;

	public CommandInterface() {
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public synchronized void initialize(String method, String pluginName, StandardCommandManager cManager) {
		TreeMap<Integer,HookContainer<PreHookInterface>> preHooks = new TreeMap<>();
		TreeMap<Integer,HookContainer<PostHookInterface>> postHooks = new TreeMap<>();

		// TODO @JRI Plug hooks
		// Populate all available pre hooks
		/*try {
			List<PluginObjectContainer<PreHookInterface>> loadedPreHooks = 
				CommonPluginUtils.getPluginObjectsInContainer(this, pluginName, "PreHook", "HookClass", "HookMethod",
						"ParentMethod", method, new Class[] {CommandRequest.class});
			for (PluginObjectContainer<PreHookInterface> container : loadedPreHooks) {
				int priority = container.getPluginExtension().getParameter("Priority").valueAsNumber().intValue();
				if (preHooks.containsKey(priority)) {
                    logger.warn("{} already has a pre hook with priority {} adding {} with next available priority", pluginName, priority, container.getPluginExtension().getId());
					while (preHooks.containsKey(priority)) {
						priority++;
					}
				}
				PreHookInterface preHookInstance = container.getPluginObject();
				preHookInstance.initialize(cManager);
				preHooks.put(priority,
                        new HookContainer<>(container.getPluginMethod(), preHookInstance));
			}
		} catch (IllegalArgumentException e) {
            logger.error("Failed to load plugins for {} extension point 'PreHook', possibly the {} extension point definition has changed in the plugin.xml", pluginName, pluginName, e);
		}*/

		// Populate all available post hooks
		/*try {
			List<PluginObjectContainer<PostHookInterface>> loadedPostHooks = 
				CommonPluginUtils.getPluginObjectsInContainer(this, pluginName, "PostHook", "HookClass", "HookMethod",
						"ParentMethod", method, new Class[] {CommandRequest.class, CommandResponse.class});
			for (PluginObjectContainer<PostHookInterface> container : loadedPostHooks) {
				int priority = container.getPluginExtension().getParameter("Priority").valueAsNumber().intValue();
				if (postHooks.containsKey(priority)) {
                    logger.warn("{} already has a post hook with priority {} adding {} with next available priority", pluginName, priority, container.getPluginExtension().getId());
					while (postHooks.containsKey(priority)) {
						priority++;
					}
				}
				PostHookInterface postHookInstance = container.getPluginObject();
				postHookInstance.initialize(cManager);
				postHooks.put(priority,
                        new HookContainer<>(container.getPluginMethod(), postHookInstance));
			}
		} catch (IllegalArgumentException e) {
            logger.error("Failed to load plugins for {} extension point 'PostHook', possibly the {} extension point definition has changed in the plugin.xml", pluginName, pluginName, e);
		}*/
		_preHooks = preHooks;
		_postHooks = postHooks;
	}

	protected void doPostHooks(CommandRequestInterface request, CommandResponseInterface response) {
		for (HookContainer<PostHookInterface> hook : _postHooks.values()) {
			Method m = hook.getMethod();
			try {
				m.invoke(hook.getHookInterfaceInstance(), request, response);
			}
			catch (Exception e) {
                logger.error("Error while loading/invoking posthook {}", m.toString(), e.getCause());
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
                logger.error("Error while loading/invoking prehook {}", m.toString(), e.getCause());
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
			reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.ISO_8859_1));
			response.addComment(reader);
			reader.close();
		} finally {
			if (reader != null)
				reader.close();
		}
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(Object event) {

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

	/**
	 * Called when the command instance has been unloaded from the parent command map. At this
	 * point the command is no longer referenced or accessible, this method performs any cleanup
	 * required at this point.
	 */
	protected void unload() {
		AnnotationProcessor.unprocess(this);
	}
}
