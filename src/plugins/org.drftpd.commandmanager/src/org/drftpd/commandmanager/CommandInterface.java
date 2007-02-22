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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.PreHookInterface;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * @author djb61
 * @version $Id$
 */
public abstract class CommandInterface {

	private static final Logger logger = Logger.getLogger(CommandInterface.class);
	
	private HashMap<Integer, Object[]> _postHooks;

	private HashMap<Integer, Object[]> _preHooks;

	private ArrayList<Integer> _postHookPriorities;

	private ArrayList<Integer> _preHookPriorities;

	public void initialize(String method, String pluginName) {
		_postHooks = new HashMap<Integer, Object[]>();
		_preHooks = new HashMap<Integer, Object[]>();
		
		PluginManager manager = PluginManager.lookup(this);

		/* Iterate through the post hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint postHookExtPoint = 
			manager.getRegistry().getExtensionPoint(pluginName, "PostHook");

		for (Iterator postHooks = postHookExtPoint.getConnectedExtensions().iterator();
			postHooks.hasNext();) { 

			Extension postHook = (Extension) postHooks.next();

			if (postHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(postHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(postHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
					}
				}
				ClassLoader postHookLoader = manager.getPluginClassLoader( 
						postHook.getDeclaringPluginDescriptor());
				try {
					Class postHookCls = postHookLoader.loadClass(
							postHook.getParameter("HookClass").valueAsString());
					PostHookInterface postHookInstance = (PostHookInterface) postHookCls.newInstance();
					postHookInstance.initialize();

					Method m = postHookInstance.getClass().getMethod(
							postHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class, CommandResponse.class});
					_postHooks.put((Integer)postHook.getParameter("Priority").valueAsNumber(),
							new Object[] {m,postHookInstance});
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add post hook handler to " +
							pluginName + " from plugin: "
							+postHook.getDeclaringPluginDescriptor().getId());
				}
			}
		}

		_postHookPriorities = new ArrayList<Integer>(_postHooks.keySet());
		Collections.sort(_postHookPriorities);
		Collections.reverse(_postHookPriorities);

		/* Iterate through the ppre hook extensions registered for this plugin
		 * and find any which belong to the method we are using in this instance,
		 * add these to a method map for later use.
		 */
		ExtensionPoint preHookExtPoint = 
			manager.getRegistry().getExtensionPoint(pluginName, "PreHook");

		for (Iterator preHooks = preHookExtPoint.getConnectedExtensions().iterator();
			preHooks.hasNext();) { 

			Extension preHook = (Extension) preHooks.next();

			if (preHook.getParameter("ParentMethod").valueAsString().equals(method)) {
				if (!manager.isPluginActivated(preHook.getDeclaringPluginDescriptor())) {
					try {
						manager.activatePlugin(preHook.getDeclaringPluginDescriptor().getId());
					}
					catch (PluginLifecycleException e) {
						// Not overly concerned about this
					}
				}
				ClassLoader preHookLoader = manager.getPluginClassLoader( 
						preHook.getDeclaringPluginDescriptor());
				try {
					Class preHookCls = preHookLoader.loadClass(
							preHook.getParameter("HookClass").valueAsString());
					PreHookInterface preHookInstance = (PreHookInterface) preHookCls.newInstance();
					preHookInstance.initialize();

					Method m = preHookInstance.getClass().getMethod(
							preHook.getParameter("HookMethod").valueAsString(),
							new Class[] {CommandRequest.class});
					_preHooks.put((Integer)preHook.getParameter("Priority").valueAsNumber(),
							new Object[] {m,preHookInstance});
				}
				catch(Exception e) {
					/* Should be safe to continue, just means this post hook won't be
					 * available
					 */
					logger.info("Failed to add pre hook handler to " +
							pluginName + " from plugin: "
							+preHook.getDeclaringPluginDescriptor().getId());
				}
			}
		}

		_preHookPriorities = new ArrayList<Integer>(_preHooks.keySet());
		Collections.sort(_preHookPriorities);
		Collections.reverse(_preHookPriorities);
	}

	protected void doPostHooks(CommandRequest request, CommandResponse response) {
		for (Integer key : _postHookPriorities) {
			Object[] hook = _postHooks.get(key);
			Method m = (Method) hook[0];
			try {
				m.invoke(hook[1], new Object[] {request, response});
			}
			catch (Exception e) {
				/* Not that important, this just means that this post hook
				 * failed and we'll just move onto the next one
				 */
			}
		}
	}

	protected CommandRequest doPreHooks(CommandRequest request) {
		CommandRequest _request = request;
		_request.setAllowed(new Boolean(true));
		for (Integer key : _preHookPriorities) {
			Object[] hook = _preHooks.get(key);
			Method m = (Method) hook[0];
			try {
				_request = (CommandRequest) m.invoke(hook[1], new Object[] {_request});
			}
			catch (Exception e) {
				/* Not that important, this just means that this pre hook
				 * failed and we'll just move onto the next one
				 */
			}
			if (!_request.getAllowed()) {
				break;
			}
		}
		return _request;
	}
}
