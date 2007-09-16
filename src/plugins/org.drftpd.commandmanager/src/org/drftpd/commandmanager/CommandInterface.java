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
import java.util.Map;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author djb61
 * @version $Id$
 */
public abstract class CommandInterface implements EventSubscriber {

	private static final Logger logger = Logger.getLogger(CommandInterface.class);
	
	protected String[] _featReplies;

	private Map<Integer, HookContainer<PostHookInterface>> _postHooks;

	private Map<Integer, HookContainer<PreHookInterface>> _preHooks;

	public CommandInterface() {
		GlobalContext.getEventService().subscribe(UnloadPluginEvent.class, this);
	}

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		_postHooks = Collections.synchronizedMap(new TreeMap<Integer, HookContainer<PostHookInterface>>());
		_preHooks = Collections.synchronizedMap(new TreeMap<Integer, HookContainer<PreHookInterface>>());
		
		PluginManager manager = PluginManager.lookup(this);

		/* Iterate through the post hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint postHookExtPoint = 
			manager.getRegistry().getExtensionPoint(pluginName, "PostHook");

		for (Extension postHook : postHookExtPoint.getConnectedExtensions()) {

			if (postHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(postHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(postHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
						logger.debug("plugin lifecycle exception", e);
					}
				}
				ClassLoader postHookLoader = manager.getPluginClassLoader( 
						postHook.getDeclaringPluginDescriptor());
				try {
					Class postHookCls = postHookLoader.loadClass(
							postHook.getParameter("HookClass").valueAsString());
					PostHookInterface postHookInstance = (PostHookInterface) postHookCls.newInstance();
					
					postHookInstance.initialize(cManager);

					Method m = postHookInstance.getClass().getMethod(
							postHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class, CommandResponse.class});
					int priority = postHook.getParameter("Priority").valueAsNumber().intValue();
					if (_postHooks.containsKey(new Integer(priority))) {
						logger.warn(pluginName + " already has a post hook with priority " +
								priority + " adding " + postHook.getId() + " with next available priority");
						while (_postHooks.containsKey(new Integer(priority))) {
							priority++;
						}
					}
					_postHooks.put(new Integer(priority),
							new HookContainer<PostHookInterface>(m,postHookInstance));
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add post hook handler to " +
							pluginName + " from plugin: "
							+postHook.getDeclaringPluginDescriptor().getId(),e);
				}
			}
		}

		/* Iterate through the pre hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint preHookExtPoint = 
			manager.getRegistry().getExtensionPoint(pluginName, "PreHook");

		for (Extension preHook : preHookExtPoint.getConnectedExtensions()) {

			if (preHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(preHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(preHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
						logger.debug("plugin lifecycle exception", e);
					}
				}
				ClassLoader preHookLoader = manager.getPluginClassLoader( 
						preHook.getDeclaringPluginDescriptor());
				try {
					Class preHookCls = preHookLoader.loadClass(
							preHook.getParameter("HookClass").valueAsString());
					PreHookInterface preHookInstance = (PreHookInterface) preHookCls.newInstance();
					
					preHookInstance.initialize(cManager);

					Method m = preHookInstance.getClass().getMethod(
							preHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class});
					int priority = preHook.getParameter("Priority").valueAsNumber().intValue();
					if (_preHooks.containsKey(new Integer(priority))) {
						logger.warn(pluginName + " already has a pre hook with priority " +
								priority + " adding " + preHook.getId() + " with next available priority");
						while (_preHooks.containsKey(new Integer(priority))) {
							priority++;
						}
					}
					_preHooks.put(new Integer(priority),
							new HookContainer<PreHookInterface>(m,preHookInstance));
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add pre hook handler to " +
							pluginName + " from plugin: "
							+preHook.getDeclaringPluginDescriptor().getId(),e);
				}
			}
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

	public void onEvent(Object event) {
		if (event instanceof UnloadPluginEvent) {
			UnloadPluginEvent pluginEvent = (UnloadPluginEvent) event;
			PluginManager manager = PluginManager.lookup(this);
			String currentPlugin = manager.getPluginFor(this).getDescriptor().getId();
			for (String pluginExtension : pluginEvent.getParentPlugins()) {
				int pointIndex = pluginExtension.lastIndexOf("@");
				String plugin = pluginExtension.substring(0, pointIndex);
				String extension = pluginExtension.substring(pointIndex+1);
				if (plugin.equals(currentPlugin) && extension.equals("PostHook")) {
					for (Iterator<Entry<Integer, HookContainer<PostHookInterface>>> iter = _postHooks.entrySet().iterator(); iter.hasNext();) {
						Entry<Integer, HookContainer<PostHookInterface>> entry = iter.next();
						if (manager.getPluginFor(entry.getValue().getHookInterfaceInstance()).getDescriptor().getId().equals(pluginEvent.getPlugin())) {
							logger.debug("Removing post hook provided by " + pluginEvent.getPlugin() + " from " + currentPlugin);
							iter.remove();
						}
					}
				}
				if (plugin.equals(currentPlugin) && extension.equals("PreHook")) {
					for (Iterator<Entry<Integer, HookContainer<PreHookInterface>>> iter = _preHooks.entrySet().iterator(); iter.hasNext();) {
						Entry<Integer, HookContainer<PreHookInterface>> entry = iter.next();
						if (manager.getPluginFor(entry.getValue().getHookInterfaceInstance()).getDescriptor().getId().equals(pluginEvent.getPlugin())) {
							logger.debug("Removing pre hook provided by " + pluginEvent.getPlugin() + " from " + currentPlugin);
							iter.remove();
						}
					}
				}
			}
		}
	}
}
