/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands.find;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.commands.find.action.ActionInterface;
import org.drftpd.commands.find.option.OptionInterface;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.master.Session;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.*;


/**
 * @author pyrrhic
 * @author mog
 * @author fr0w
 * @author scitz0
 * @version $Id$
 */
public class Find extends CommandInterface {
	public static final Logger logger = LogManager.getLogger(Find.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	private CaseInsensitiveHashMap<String, OptionInterface> _optionsMap = new CaseInsensitiveHashMap<>();
	private CaseInsensitiveHashMap<String, ActionInterface> _actionsMap = new CaseInsensitiveHashMap<>();

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";

		// Subscribe to events
		AnnotationProcessor.process(this);

		// Load all options
		try {
			List<PluginObjectContainer<OptionInterface>> loadedOptions =
				CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.commands.find", "Option", "ClassName", true);
			for (PluginObjectContainer<OptionInterface> container : loadedOptions) {
				String optionName = container.getPluginExtension().getParameter("OptionName").valueAsString();
				_optionsMap.put("-" + optionName, container.getPluginObject());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load options for org.drftpd.commands.find extension point 'Option'"
					+", possibly the org.drftpd.commands.find"
					+" extension point definition has changed in the plugin.xml",e);
		}

		// Load all actions
		try {
			List<PluginObjectContainer<ActionInterface>> loadedActions =
				CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.commands.find", "Action", "ClassName", true);
			for (PluginObjectContainer<ActionInterface> container : loadedActions) {
				String actionName = container.getPluginExtension().getParameter("ActionName").valueAsString();
				_actionsMap.put("-" + actionName, container.getPluginObject());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load options for org.drftpd.commands.find extension point 'Action'"
					+", possibly the org.drftpd.commands.find"
					+" extension point definition has changed in the plugin.xml",e);
		}
	}

	public CommandResponse doFIND(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		DirectoryHandle dir = request.getCurrentDirectory();

		User user = request.getSession().getUserNull(request.getUser());

		ArrayList<ActionInterface> actions = new ArrayList<>();

		int limit = Integer.parseInt(request.getProperties().getProperty("limit.default","5"));
		int maxLimit = Integer.parseInt(request.getProperties().getProperty("limit.max","20"));

		boolean quiet = false;

		LinkedList<String> args = new LinkedList<>(Arrays.asList(request.getArgument().split("\\s+")));

		if (args.isEmpty()) {
			throw new ImproperUsageException();
		}

		while(!args.isEmpty()) {
			String arg = args.poll();

			if (arg.equalsIgnoreCase("-quiet")) {
				quiet = true;
			} else if (arg.equalsIgnoreCase("-limit")) {
				if (args.isEmpty()) {
					throw new ImproperUsageException();
				}
				try {
					int newLimit = Integer.parseInt(args.poll());
					if (newLimit > 0 && newLimit < maxLimit) {
						limit = newLimit;
					} else {
						limit = maxLimit;
					}
				} catch (NumberFormatException e) {
					throw new ImproperUsageException("Limit must be valid number.");
				}
			} else if (arg.equalsIgnoreCase("-section")) {
				if (args.isEmpty()) {
					throw new ImproperUsageException();
				}
				dir = GlobalContext.getGlobalContext().getSectionManager().
						getSection(args.poll()).getBaseDirectory();
			} else if (_optionsMap.containsKey(arg)) {
				// Check if arg matches any of the loaded options
				try {
					OptionInterface option = _optionsMap.get(arg);
					option.exec(arg, getArgs(args), params);
				} catch (Exception e) {
                    logger.debug("Option = {}", arg, e);
					return new CommandResponse(500, e.getMessage());
				}
			} else if (_actionsMap.containsKey(arg)) {
				// Check if arg matches any of the loaded actions
				if (!checkCustomPermission(request, arg.toLowerCase(), "*")) {
					return new CommandResponse(500, "You do not have the proper permissions for " + arg);
				}
				try {
					ActionInterface action = _actionsMap.get(arg);
					action.initialize(arg, getArgs(args));
					actions.add(action);
				} catch (Exception e) {
                    logger.debug("Action = {}", arg, e);
					return new CommandResponse(500, e.getMessage());
				}
			} else {
				// Switch doesn't exist
				throw new ImproperUsageException();
			}
		}

		if (actions.isEmpty()) {
			throw new ImproperUsageException();
		}

		params.setLimit(0); // Get all results, we filter out hidden inodes later

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		try {
			inodes = ie.advancedFind(dir, params);
		} catch (IndexException e) {
			logger.error(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.info(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		}

		ReplacerEnvironment env = new ReplacerEnvironment();

		Session session = request.getSession();

		CommandResponse response = new CommandResponse(200, "Find complete!");

		if (inodes.isEmpty()) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"find.empty", env, user.getName()));
			return response;
		}

		LinkedList<String> responses = new LinkedList<>();
		int results = 0;
		boolean observePrivPath = request.getProperties().
				getProperty("observe.privpath","true").equalsIgnoreCase("true");
		
		InodeHandle inode;
		for (Map.Entry<String,String> item : inodes.entrySet()) {
			if (results == limit)
				break;
			try {
				inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey().
						substring(0, item.getKey().length()-1)) : new FileHandle(item.getKey());
				if ((observePrivPath && inode.isHidden(user)) || (!observePrivPath && inode.isHidden(null))) {
					continue;
				}
				env.add("name", inode.getName());
				env.add("path", inode.getPath());
				env.add("owner", inode.getUsername());
				env.add("group", inode.getGroup());
				env.add("size", Bytes.formatBytes(inode.getSize()));
				for (ActionInterface action : actions) {
					if ((inode.isFile() && action.execInFiles()) ||
							(inode.isDirectory() && action.execInDirs())) {
                        logger.debug("Action {} executing on {}", action.getClass(), inode.getPath());
						String text = action.exec(request, inode);
						if (!quiet || action.failed())
							responses.add(text);
					}
				}
				results++;
			} catch (FileNotFoundException e) {
                logger.warn("Index contained an unexistent inode: {}", item.getKey());
			}
		}

		if (results == 0) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"find.empty", env, user.getName()));
			return response;
		}

		env.add("limit", limit);
		env.add("results", results);
		response.addComment(session.jprintf(_bundle,_keyPrefix+"find.header", env, user.getName()));

		for (String line : responses) {
			response.addComment(line);
		}

		return response;
	}

	/**
	 * Gets all arguments for this option/action. Multiple args must be enclosed with '"'.
	 *
	 * @param st
	 *        The <tt>LinkedList</tt> containing all arguments.
	 *
	 * @return A String array containing all arguments belonging to this option/action.
	 *         Null if LinkedList does not contain any more elements or if next element
	 *         starts with the '-' character.
	 *
	 * @throws ImproperUsageException
	 *         Thrown if end '"' is missing.
	 */
	private String[] getArgs(LinkedList<String> text) throws ImproperUsageException {
		if (text.isEmpty() || text.peek().startsWith("-")) {
			return null;
		}
		String args = text.poll();
		if (args.charAt(0) == '"') {
			args = args.substring(1);
			while (true) {
				if (args.endsWith("\"")) {
					args = args.substring(0,args.length()-1);
					break;
				} else if (text.isEmpty()) {
					throw new ImproperUsageException();
				} else {
					args += " " + text.poll();
				}
			}
		}
		return args.split(" ");
	}

	@EventSubscriber
	@Override
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		super.onUnloadPluginEvent(event);
		Collection<OptionInterface> unloadedOptions =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "Option", event, _optionsMap.values());
		if (!unloadedOptions.isEmpty()) {
			CaseInsensitiveHashMap<String, OptionInterface> clonedOptionAddons = new CaseInsensitiveHashMap<>(_optionsMap);
			boolean addonRemoved = false;
			for (Iterator<Map.Entry<String, OptionInterface>> iter = clonedOptionAddons.entrySet().iterator(); iter.hasNext();) {
				OptionInterface optionAddon = iter.next().getValue();
				if (unloadedOptions.contains(optionAddon)) {
                    logger.debug("Unloading FIND option addon provided by plugin {}", CommonPluginUtils.getPluginIdForObject(optionAddon));
					iter.remove();
					addonRemoved = true;
				}
			}
			if (addonRemoved) {
				_optionsMap = clonedOptionAddons;
			}
		}
		Collection<ActionInterface> unloadedActions =
			MasterPluginUtils.getUnloadedExtensionObjects(this, "Action", event, _actionsMap.values());
		if (!unloadedActions.isEmpty()) {
			CaseInsensitiveHashMap<String, ActionInterface> clonedActionAddons = new CaseInsensitiveHashMap<>(_actionsMap);
			boolean addonRemoved = false;
			for (Iterator<Map.Entry<String, ActionInterface>> iter = clonedActionAddons.entrySet().iterator(); iter.hasNext();) {
				ActionInterface actionAddon = iter.next().getValue();
				if (unloadedActions.contains(actionAddon)) {
                    logger.debug("Unloading FIND action addon provided by plugin {}", CommonPluginUtils.getPluginIdForObject(actionAddon));
					iter.remove();
					addonRemoved = true;
				}
			}
			if (addonRemoved) {
				_actionsMap = clonedActionAddons;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		// Load all options
		try {
			List<PluginObjectContainer<OptionInterface>> loadedOptions =
				MasterPluginUtils.getLoadedExtensionObjectsInContainer(this, "org.drftpd.commands.find", "Option", event, "ClassName");
			if (!loadedOptions.isEmpty()) {
				CaseInsensitiveHashMap<String, OptionInterface> clonedOptionAddons = new CaseInsensitiveHashMap<>(_optionsMap);
				for (PluginObjectContainer<OptionInterface> container : loadedOptions) {
					String optionName = container.getPluginExtension().getParameter("OptionName").valueAsString();
					clonedOptionAddons.put("-" + optionName, container.getPluginObject());
				}
				_optionsMap = clonedOptionAddons;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load options for org.drftpd.commands.find extension point 'Option'"
					+", possibly the org.drftpd.commands.find"
					+" extension point definition has changed in the plugin.xml",e);
		}

		// Load all actions
		try {
			List<PluginObjectContainer<ActionInterface>> loadedActions =
				MasterPluginUtils.getLoadedExtensionObjectsInContainer(this, "org.drftpd.commands.find", "Action", event, "ClassName");
			if (!loadedActions.isEmpty()) {
				CaseInsensitiveHashMap<String, ActionInterface> clonedActionAddons = new CaseInsensitiveHashMap<>(_actionsMap);
				for (PluginObjectContainer<ActionInterface> container : loadedActions) {
					String actionName = container.getPluginExtension().getParameter("ActionName").valueAsString();
					clonedActionAddons.put("-" + actionName, container.getPluginObject());
				}
				_actionsMap = clonedActionAddons;
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load options for org.drftpd.commands.find extension point 'Action'"
					+", possibly the org.drftpd.commands.find"
					+" extension point definition has changed in the plugin.xml",e);
		}
	}
}
