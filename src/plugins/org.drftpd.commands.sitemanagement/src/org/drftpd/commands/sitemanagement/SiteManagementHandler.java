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

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.helpers.OptionConverter;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.java.plugin.JpfException;
import org.java.plugin.PluginManager;
import org.java.plugin.boot.DefaultPluginsCollector;
import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.util.ExtendedProperties;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SiteManagementHandler extends CommandInterface {
	private static final Logger logger = Logger.getLogger(SiteManagementHandler.class);

	private static final String jpfConf = "conf/boot-master.properties";

	public CommandResponse doSITE_LIST(CommandRequest request) {

		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		
		DirectoryHandle dir = request.getCurrentDirectory();
		InodeHandle target;
		if (request.hasArgument()) {
			try {
				target = dir.getInodeHandle(request.getArgument());
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
				inodes = Collections.singletonList((InodeHandle)dir);
			} else {
				inodes = new ArrayList<InodeHandle>(dir.getInodeHandles());
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
		ExtendedProperties jpfProps = new ExtendedProperties();
		FileInputStream fis = null;
		try {
			fis = new FileInputStream(jpfConf);
			jpfProps.load(fis);
		}
		catch (IOException e) {
			logger.debug("Exception loading JPF properties",e);
			return new CommandResponse(500,e.getMessage());
		}
		finally {
			if (fis != null) {
				try {
					fis.close();
				}
				catch (IOException e) {
					logger.debug("Exception closing input stream",e);
				}
			}
		}
		PluginManager manager = PluginManager.lookup(this);
		DefaultPluginsCollector collector = new DefaultPluginsCollector();
		try {
			collector.configure(jpfProps);
		}
		catch (Exception e) {
			logger.debug("Exception configuring plugins collector",e);
			return new CommandResponse(500,e.getMessage());
		}
		try {
			manager.publishPlugins(collector.collectPluginLocations().toArray(new PluginManager.PluginLocation[0]));
		}
		catch (JpfException e) {
			logger.debug("Exception publishing plugins", e);
			return new CommandResponse(500,e.getMessage());
		}
		PluginDescriptor newPlugin;
		try {
			newPlugin = manager.getRegistry().getPluginDescriptor(request.getArgument());
		}
		catch (IllegalArgumentException e) {
			return new CommandResponse(500, "No such plugin could be found");
		}
		if (manager.isPluginActivated(newPlugin)) {
			return new CommandResponse(500, "Plugin is already loaded and active");
		}
		GlobalContext.getEventService().publish(new LoadPluginEvent(request.getArgument()));
		return new CommandResponse(200, "Successfully loaded plugin");
	}

	public CommandResponse doSITE_PLUGINS(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment("Plugins loaded:");
		ArrayList<String> plugins = new ArrayList<String>();
		for (PluginDescriptor pluginDesc : PluginManager.lookup(this).getRegistry().getPluginDescriptors()) {
			plugins.add(pluginDesc.getId());
		}
		Collections.sort(plugins);
		for (String pluginName : plugins) {
			response.addComment(pluginName);
		}
		response.addComment(plugins.size()+" plugins currently loaded.");
		return response;
	}

	public CommandResponse doSITE_RELOAD(CommandRequest request) {

		try {
			GlobalContext.getGlobalContext().getSectionManager().reload();
			GlobalContext.getGlobalContext().reloadFtpConfig();
			GlobalContext.getGlobalContext().loadPluginsConfig();
			GlobalContext.getGlobalContext().getSlaveSelectionManager().reload();
	
			GlobalContext.getEventService().publish(new ReloadEvent(PluginManager.lookup(this).getPluginFor(this).getDescriptor().getId()));

		} catch (IOException e) {
			logger.log(Level.FATAL, "Error reloading config", e);

			return new CommandResponse(200, e.getMessage());
		}

		// ugly hack to clear resourcebundle cache
		// see
		// http://developer.java.sun.com/developer/bugParade/bugs/4212439.html
		try {
			Field cacheList = ResourceBundle.class
					.getDeclaredField("cacheList");
			cacheList.setAccessible(true);
			((Map) cacheList.get(ResourceBundle.class)).clear();
			cacheList.setAccessible(false);
		} catch (Exception e) {
			logger.error("", e);
		}

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
		GlobalContext.getEventService().publish(new UnloadPluginEvent(request.getArgument()));
		manager.deactivatePlugin(request.getArgument());
		if (manager.isPluginActivated(pluginDesc)) {
			return new CommandResponse(500, "Unable to unload plugin");
		}
		manager.getRegistry().unregister(new String[] {request.getArgument()});
		return new CommandResponse(200, "Successfully unloaded your plugin");
	}
}
