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
package org.drftpd.commands.sitemanagement;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commandmanager.*;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.master.Session;
import org.drftpd.usermanager.User;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.java.plugin.JpfException;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.boot.DefaultPluginsCollector;
import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.PluginRegistry;
import org.java.plugin.util.ExtendedProperties;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SiteManagementHandler extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(SiteManagementHandler.class);

	private static final String jpfConf = "conf/boot-master.properties";

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSITE_LIST(CommandRequest request) {

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

		DirectoryHandle dir = request.getCurrentDirectory();
		InodeHandle target;
		User user = request.getSession().getUserNull(request.getUser());

		if (request.hasArgument()) {
			try {
				target = dir.getInodeHandle(request.getArgument(), user);
			} catch (FileNotFoundException e) {
				logger.debug("FileNotFound", e);
				return new CommandResponse(200, e.getMessage());
			}
		} else {
			target = dir;
		}

		List<InodeHandle> inodes;
		try {
			if (target.isFile()) {
				inodes = Collections.singletonList(dir);
			} else {
				inodes = new ArrayList<>(dir.getInodeHandles(user));
			}
			Collections.sort(inodes);

			for (InodeHandle inode : inodes) {
				response.addComment(inode.toString());
			}
		} catch (FileNotFoundException e) {
			logger.debug("FileNotFound", e);
			return new CommandResponse(200, e.getMessage());
		}
		return response;
	}

	public CommandResponse doSITE_LOADPLUGIN(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		Session session = request.getSession();
		PluginDescriptor newPlugin;
		PluginManager manager = PluginManager.lookup(this);
		try {
			newPlugin = manager.getRegistry().getPluginDescriptor(request.getArgument());
		} catch (IllegalArgumentException e) {
			ExtendedProperties jpfProps = new ExtendedProperties();
			FileInputStream fis = null;
			try {
				fis = new FileInputStream(jpfConf);
				jpfProps.load(fis);
			} catch (IOException e2) {
				logger.debug("Exception loading JPF properties",e);
				return new CommandResponse(500,e2.getMessage());
			}
			finally {
				if (fis != null) {
					try {
						fis.close();
					} catch (IOException e2) {
						// Stream is already closed
					}
				}
			}
			DefaultPluginsCollector collector = new DefaultPluginsCollector();
			try {
				collector.configure(jpfProps);
			} catch (Exception e2) {
				logger.debug("Exception configuring plugins collector",e2);
				return new CommandResponse(500,e2.getMessage());
			}
			try {
				manager.publishPlugins(collector.collectPluginLocations().toArray(new PluginManager.PluginLocation[0]));
			} catch (JpfException e2) {
				logger.debug("Exception publishing plugins", e2);
				return new CommandResponse(500,e2.getMessage());
			}
		}
		try {
			newPlugin = manager.getRegistry().getPluginDescriptor(request.getArgument());
		} catch (IllegalArgumentException e) {
			return new CommandResponse(500, "No such plugin could be found");
		}
		if (manager.isPluginActivated(newPlugin)) {
			return new CommandResponse(500, "Plugin is already loaded and active");
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		for (PluginDescriptor loadPlugin : getPluginsToLoad(newPlugin, manager)) {
			env.add("plugin.name",loadPlugin.getId());
			try {
				manager.activatePlugin(loadPlugin.getId());
			} catch (PluginLifecycleException e) {
				session.printOutput(200, session.jprintf(_bundle,
						_keyPrefix+"plugin.load.failure", env, request.getUser()));
                logger.warn("Error starting plugin {}", loadPlugin.getId(), e);
				return new CommandResponse(500, "Plugin instantiation failed");
			}
			GlobalContext.getEventService().publish(new LoadPluginEvent(loadPlugin.getId()));
			session.printOutput(200, session.jprintf(_bundle,
					_keyPrefix+"plugin.load.success", env, request.getUser()));
		}
		return new CommandResponse(200, "Successfully loaded plugin");
	}

	public CommandResponse doSITE_PLUGINS(CommandRequest request) {
		Session session = request.getSession();
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();
		response.addComment(session.jprintf(_bundle,
				_keyPrefix+"plugins.header", env, request.getUser()));

		TreeMap<String,String> plugins = new TreeMap<>();
		PluginManager manager = PluginManager.lookup(this);
		for (PluginDescriptor pluginDesc : manager.getRegistry().getPluginDescriptors()) {
			if (manager.isBadPlugin(pluginDesc)) {
				plugins.put(pluginDesc.getId(),"BAD");
			} else if (manager.isPluginActivated(pluginDesc)) {
				plugins.put(pluginDesc.getId(),"Active");
			} else {
				plugins.put(pluginDesc.getId(),"Inactive");
			}
		}
		for(Entry<String,String> entry : plugins.entrySet()) {
			env.add("id",entry.getKey());
			env.add("status",entry.getValue());
			response.addComment(session.jprintf(_bundle,
					_keyPrefix+"plugins.info", env, request.getUser()));
		}
		env.add("total",plugins.size());
		response.addComment(session.jprintf(_bundle,
				_keyPrefix+"plugins.total", env, request.getUser()));

		return response;
	}

	public CommandResponse doSITE_RELOAD(CommandRequest request) {

		try {
			GlobalContext.getGlobalContext().getSectionManager().reload();
			GlobalContext.getGlobalContext().reloadFtpConfig();
			GlobalContext.getGlobalContext().loadPluginsConfig();
			GlobalContext.getGlobalContext().getSlaveSelectionManager().reload();

			GlobalContext.getEventService().publish(new ReloadEvent(CommonPluginUtils.getPluginIdForObject(this)));

		} catch (IOException e) {
			logger.log(Level.FATAL, "Error reloading config", e);

			return new CommandResponse(200, e.getMessage());
		}

		PluginManager manager = PluginManager.lookup(this);
		for (PluginDescriptor descr : manager.getRegistry().getPluginDescriptors()) {
			ResourceBundle.clearCache(manager.getPluginClassLoader(descr));
		}
		// Clear base system classloader also
		ResourceBundle.clearCache(ClassLoader.getSystemClassLoader());
        /*
		try {
			OptionConverter.selectAndConfigure(
					new URL(PropertyHelper.getProperty(System.getProperties(),
					"log4j.configuration")), null, LogManager
					.getLoggerRepository());
		} catch (MalformedURLException e) {
			logger.error(e);
			return new CommandResponse(500, e.getMessage());
		} finally {
		}
		*/
		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_SHUTDOWN(CommandRequest request) {

		String message;

		if (!request.hasArgument()) {
			message = "Service shutdown issued by "
				+ request.getUser();
		} else {
			message = request.getArgument();
		}

		GlobalContext.getGlobalContext().shutdown(message);

		return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	}

	public CommandResponse doSITE_UNLOADPLUGIN(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		Session session = request.getSession();
		PluginManager manager = PluginManager.lookup(this);
		PluginDescriptor pluginDesc;
		try {
			pluginDesc = manager.getRegistry().getPluginDescriptor(request.getArgument());
		} catch (IllegalArgumentException e) {
			return new CommandResponse(500, "No such plugin loaded");
		}
		/* Check whether this plugin allows unloading, to do this an attribute
		 * named DenyUnload is set to "true" in the plugins manifest, if this
		 * attribute does not exist or contains anything else it is assumed that
		 * unloading is permitted.
		 */
		PluginAttribute unloadAttribute = pluginDesc.getAttribute("DenyUnload");
		if (unloadAttribute != null) {
			if (unloadAttribute.getValue().equalsIgnoreCase("true")) {
				return new CommandResponse(500, "Unloading of this plugin is prohibited");
			}
		}
		
		/* Check whether all descdendants of this plugin allow unloading, 
		 * to do this an attribute named DenyUnload is set to "true" in the 
		 * plugins manifest, if this attribute does not exist or contains 
		 * anything else it is assumed that unloading is permitted.
		 */
		List<PluginDescriptor> unloadingPlugins = getPluginsToUnload(pluginDesc, manager.getRegistry());
		List<String> prohibitedPlugins = new ArrayList<>();
		for (PluginDescriptor unloadPlugin : unloadingPlugins) {
			if (!unloadPlugin.getId().equals(pluginDesc.getId())) {
				unloadAttribute = unloadPlugin.getAttribute("DenyUnload");
				if (unloadAttribute != null) {
					if (unloadAttribute.getValue().equalsIgnoreCase("true")) {
						prohibitedPlugins.add(unloadPlugin.getId());
					}
				}
			}
		}
		if (!prohibitedPlugins.isEmpty()) {
			CommandResponse response = new CommandResponse(500, "Unloading of this plugin is prohibited");
			response.addComment("The following descendant plugins are prohibited from unloading");
			for (String deniedPlugin : prohibitedPlugins) {
				response.addComment(deniedPlugin);
			}
			return response;
		}
		
		ReplacerEnvironment env = new ReplacerEnvironment();
		for (PluginDescriptor unloadPlugin : unloadingPlugins) {
			if (manager.isPluginActivated(unloadPlugin)) {
				GlobalContext.getEventService().publish(new UnloadPluginEvent(unloadPlugin.getId()));
				manager.deactivatePlugin(unloadPlugin.getId());
				env.add("plugin.name",unloadPlugin.getId());
				session.printOutput(200, session.jprintf(_bundle,
						_keyPrefix+"plugin.unloaded", env, request.getUser()));
			}
		}
		manager.getRegistry().unregister(new String[] {pluginDesc.getId()});
		/* The following is a rather nasty hack but unfortunately appears to be the only way
		 * to do this. When plugins are loaded from .jar archives doing an unloadplugin, recompile
		 * with altered code, loadplugin will not work. This is because internally the JVM caches
		 * certain characteristics of the jar, such as length. Even though JPF will use a completely
		 * new classloader instance this caching remains, this leads to failure with issues like
		 * truncated class errors.
		 * Sun do not provide a way to clear this cache so we use reflection on an internal sun class
		 * to invalidate the cache on reload. This is far from ideal and limits us to Sun JVMs (though
		 * I'm pretty sure we were anyway) and is of course liable to change in future JDK versions, if 
		 * that happens we will have to revisit this.
		 * I did ponder trying to just invalidate the required entries rather than the entire cache but
		 * firstly as the jars are local and unloadplugin not a frequent operation I thought this was
		 * unnecessary, secondly this would tie us to the exact format of the private map rather than
		 * just its presence, making future incompatibilities more likely.
		 */
		try {
			Class<?> jarFileFactory = Class.forName("sun.net.www.protocol.jar.JarFileFactory");

			Field fileCache = jarFileFactory.getDeclaredField("fileCache");
			Field urlCache = jarFileFactory.getDeclaredField("urlCache");

			fileCache.setAccessible(true);
			((Map<?,?>) fileCache.get(jarFileFactory)).clear();
			fileCache.setAccessible(false);

			urlCache.setAccessible(true);
			((Map<?,?>) urlCache.get(jarFileFactory)).clear();
			urlCache.setAccessible(false);

		} catch (Exception e) {
            logger.error("Exception while clearing jar URL cache: {}", e.getMessage(), e);
		}

		return new CommandResponse(200, "Successfully unloaded your plugin");
	}

	public CommandResponse doSITE_RELOADPLUGIN(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		CommandResponse response = doSITE_UNLOADPLUGIN(request);
		if (response.getCode() == 200) {
			response = doSITE_LOADPLUGIN(request);
			if (response.getCode() == 200) {
				response = doSITE_RELOAD(request);
			}
		}
		if (response.getCode() == 200) {
			return new CommandResponse(200, "Successfully reloaded plugin");
		}
		return response;
	}

	private List<PluginDescriptor> getPluginsToUnload(PluginDescriptor unloadingPlugin, PluginRegistry registry) {
		ArrayList<PluginDescriptor> unloadingPlugins = new ArrayList<>();
		unloadingPlugins.add(unloadingPlugin);
		for (PluginDescriptor descr : registry.getPluginDescriptors()) {
			if (descr != unloadingPlugin) {
				if (isPluginDependant(descr, unloadingPlugin, registry)) {
					unloadingPlugins.add(descr);
				}
			}
		}
		reorderPlugins(unloadingPlugins, registry, true);
		return unloadingPlugins;
	}

	private List<PluginDescriptor> getPluginsToLoad(PluginDescriptor loadingPlugin, PluginManager manager) {
		ArrayList<PluginDescriptor> loadingPlugins = new ArrayList<>();
		loadingPlugins.add(loadingPlugin);
		for (PluginDescriptor descr : manager.getRegistry().getPluginDescriptors()) {
			if (descr != loadingPlugin) {
				if (!manager.isPluginActivated(descr) && isPluginDependant(loadingPlugin, descr, manager.getRegistry())) {
					loadingPlugins.add(descr);
				}
			}
		}
		reorderPlugins(loadingPlugins, manager.getRegistry(), false);
		return loadingPlugins;
	}

	private boolean isPluginDependant(PluginDescriptor plugin1, PluginDescriptor plugin2, PluginRegistry registry) {
		// Circular (mutual) dependencies are treated as absence of dependency
		// at all.
		Set<PluginDescriptor> pre1 = new HashSet<>();
		Set<PluginDescriptor> pre2 = new HashSet<>();
		collectPluginPrerequisites(plugin1, pre1, registry);
		collectPluginPrerequisites(plugin2, pre2, registry);
		return pre1.contains(plugin2) && !pre2.contains(plugin1);
	}

	private void collectPluginPrerequisites(PluginDescriptor descr, Set<PluginDescriptor> result, PluginRegistry registry) {
		for (PluginPrerequisite pre : descr.getPrerequisites()) {
			if (!pre.matches()) {
				continue;
			}
			PluginDescriptor descriptor = registry.getPluginDescriptor(pre.getPluginId());
			if (result.add(descriptor)) {
				collectPluginPrerequisites(descriptor, result, registry);
			}
		}
	}

	private void reorderPlugins(List<PluginDescriptor> plugins, PluginRegistry registry, boolean reverse) {
		for (int i = 0; i < plugins.size(); i++) {
			for (int j = i + 1; j < plugins.size(); j++) {
				if (isPluginDependant(plugins.get(i), plugins.get(j), registry)) {
					Collections.swap(plugins, i, j);
					i = -1;
					break;
				}
			}
		}
		if (reverse) {
			Collections.reverse(plugins);
		}
	}
}
