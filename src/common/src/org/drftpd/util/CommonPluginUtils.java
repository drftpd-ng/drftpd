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
package org.drftpd.util;

import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.java.plugin.Plugin;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

/**
 * A collection of helper methods to perform common tasks with the plugin framework.
 * This class provides static methods to perform operations using the underlying plugin
 * framework which are commonly required when writing new plugins. Use of these methods
 * abstracts away much of the work with the framework API to allow use of this functionality
 * without needing an indepth understanding of how the plugin framework operates.
 * 
 * @author djb61
 * @version $Id$
 */
public class CommonPluginUtils {

	private static final Logger logger = Logger.getLogger(CommonPluginUtils.class);

	/**
	 * Returns the classloader which was used to load the class defining the object.
	 * If the defining class is part of a plugin then the relevant classloader
	 * for the plugin is returned, if the class was loaded outside of the plugin
	 * framework then the base system classloader instance is returned.
	 * 
	 * @param  obj
	 *         <tt>Object</tt> to find the <tt>ClassLoader</tt> for
	 * 
	 * @return  The <tt>ClassLoader</tt> which loaded the <tt>Class</tt> definition for the <tt>Object</tt>
	 */
	public static ClassLoader getClassLoaderForObject(Object obj) {
		ClassLoader loader = ClassLoader.getSystemClassLoader();
		PluginManager manager = PluginManager.lookup(obj);
		if (manager != null) {
			Plugin plugin = manager.getPluginFor(obj);
			if (plugin != null) {
				loader = manager.getPluginClassLoader(plugin.getDescriptor());
			}
		}
		return loader;
	}

	/**
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt>.
	 * Each plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>.
	 * 
	 * @param  caller
	 *         The object instance calling this method
	 *
	 * @param  pluginName
	 *         The name of the plugin defining the extension point
	 *
	 * @param  extName
	 *         The name of the extension point in the plugin to get plugin objects for
	 *
	 * @param  classParamName
	 *         The name of the parameter in the extension point containing the class name to instantiate
	 *
	 * @return	A <tt>List</tt> containing an instance of the class from each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<T> getPluginObjects(Object caller, String pluginName, String extName, String classParamName) 
	throws IllegalArgumentException {
		List<T> objList = null;
		try {
			objList = getPluginObjects(caller, pluginName, extName, classParamName, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return objList;
	}

	/**
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt>.
	 * 
	 * @param  caller
	 *         The object instance calling this method
	 *
	 * @param  pluginName
	 *         The name of the plugin defining the extension point
	 *
	 * @param  extName
	 *         The name of the extension point in the plugin to get plugin objects for
	 *
	 * @param  classParamName
	 *         The name of the parameter in the extension point containing the class name to instantiate
	 *
	 * @param  activatePlugin
	 *         If <tt>true</tt> then each plugin found will be activated in the plugin framework if it
	 *         is not already active.
	 *
	 * @param  logError
	 *         If <tt>true</tt> then any errors encounted whilst loading plugins will be logged to the
	 *         standard log files.
	 *
	 * @param  failOnError
	 *         If <tt>true</tt> then the method will fail and throw an exception if loading a plugin fails.
	 *
	 * @return  A <tt>List</tt> containing an instance of the class from each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  ClassNotFoundException
	 *          If the class in the extension definition of a plugin cannot be found and <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  IllegalAccessException
	 *          If the class in the extension definition of a plugin has no default constructor and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 *
	 * @throws  InstantiationException
	 *          If the class in the extension definition of a plugin is not a concrete class and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  PluginLifecycleException
	 *          If a plugin cannot be activated and <tt>failOnError</tt> is <tt>true</tt>
	 */
	@SuppressWarnings("unchecked")
	public static <T> List<T> getPluginObjects(Object caller, String pluginName, String extName, String classParamName,
			boolean activatePlugin, boolean logError, boolean failOnError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException, PluginLifecycleException {
		List<T> pluginObjs = new ArrayList<T>();
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					pluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				if (activatePlugin) {
					manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
				}
				ClassLoader pluginLoader = manager.getPluginClassLoader( 
						plugin.getDeclaringPluginDescriptor());
				Class<?> pluginCls = pluginLoader.loadClass( 
						plugin.getParameter(classParamName).valueAsString());
				pluginObjs.add((T)pluginCls.newInstance());
			} catch (ClassNotFoundException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)+" not found",e);
				}
				if (failOnError) {
					throw e;
				}
			} catch (IllegalAccessException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)
							+" has no default constructor",e);
				}
				if (failOnError) {
					throw e;
				}
			} catch (InstantiationException e) {
				if (logError) {
					logger.warn("Error loading plugin"+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)
							+" is not a concrete class",e);
				}
				if (failOnError) {
					throw e;
				}
			} catch (PluginLifecycleException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", plugin not found or can't be activated",e);
				}
				if (failOnError) {
					throw e;
				}
			}
		}
		return pluginObjs;
	}

	/**
	 * Get an instance of a plugin class extending the given parent plugin at the given extension point provided
	 * by the given plugin.
	 * If the child plugin is not activated in the plugin framework then it will be activated
	 * by this method, if any error is encountered loading the desired plugin then this will be logged to the standard
	 * log files and an exception thrown.
	 *
	 * @param  caller
	 *         The object instance calling this method
	 *
	 * @param  parentPluginName
	 *         The name of the plugin defining the extension point
	 *
	 * @param  extName
	 *         The name of the extension point in the plugin to get plugin objects for
	 *
	 * @param  classParamName
	 *         The name of the parameter in the extension point containing the class name to instantiate
	 *
	 * @param  desiredPlugin
	 *         The name of the plugin providing the extension to be loaded
	 *
	 * @return  An instance of the class implementing the extension in the child plugin
	 *
	 * @throws  ClassNotFoundException
	 *          If the class in the extension definition of the requested plugin cannot be found
	 *
	 * @throws  IllegalAccessException
	 *          If the class in the extension definition of the requested plugin has no default constructor
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist or the requested plugin does not implement this extension point
	 *
	 * @throws  InstantiationException
	 *          If the class in the extension definition of a plugin is not a concrete class
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	public static <T> T getSinglePluginObject(Object caller, String parentPluginName, String extName, String classParamName,
			String desiredPlugin) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException, PluginLifecycleException {
		return CommonPluginUtils.<T>getSinglePluginObject(caller, parentPluginName, extName, classParamName, desiredPlugin, true, true);
	}

	/**
	 * Get an instance of a plugin class extending the given parent plugin at the given extension point provided
	 * by the given plugin.
	 * 
	 * @param  caller
	 *         The object instance calling this method
	 *
	 * @param  parentPluginName
	 *         The name of the plugin defining the extension point
	 *
	 * @param  extName
	 *         The name of the extension point in the plugin to get plugin objects for
	 *
	 * @param  classParamName
	 *         The name of the parameter in the extension point containing the class name to instantiate
	 *
	 * @param  desiredPlugin
	 *         The name of the plugin providing the extension to be loaded
	 *
	 * @param  activatePlugin
	 *         If <tt>true</tt> then the requested plugin will be activated in the plugin framework if it
	 *         is not already active.
	 *
	 * @param  logError
	 *         If <tt>true</tt> then any errors encounted whilst loading the requested plugin will be logged to the
	 *         standard log files.
	 *
	 * @return  An instance of the class implementing the extension in the child plugin
	 *
	 * @throws  ClassNotFoundException
	 *          If the class in the extension definition of the requested plugin cannot be found
	 *
	 * @throws  IllegalAccessException
	 *          If the class in the extension definition of the requested plugin has no default constructor
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist or the requested plugin does not implement this extension point
	 *
	 * @throws  InstantiationException
	 *          If the class in the extension definition of a plugin is not a concrete class
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	@SuppressWarnings("unchecked")
	public static <T> T getSinglePluginObject(Object caller, String parentPluginName, String extName, String classParamName,
			String desiredPlugin, boolean activatePlugin, boolean logError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException, PluginLifecycleException {
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					parentPluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				if (plugin.getDeclaringPluginDescriptor().getId().equals(desiredPlugin)) {
					if (activatePlugin) {
						manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
					}
					ClassLoader pluginLoader = manager.getPluginClassLoader( 
							plugin.getDeclaringPluginDescriptor());
					Class<?> pluginCls = pluginLoader.loadClass( 
							plugin.getParameter(classParamName).valueAsString());
					return (T)pluginCls.newInstance();
				}
			} catch (ClassNotFoundException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)+" not found",e);
				}
				throw e;
			} catch (IllegalAccessException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)
							+" has no default constructor",e);
				}
				throw e;
			} catch (InstantiationException e) {
				if (logError) {
					logger.warn("Error loading plugin"+plugin.getDeclaringPluginDescriptor().getId()
							+", requested class "+plugin.getParameter(classParamName)
							+" is not a concrete class",e);
				}
				throw e;
			} catch (PluginLifecycleException e) {
				if (logError) {
					logger.warn("Error loading plugin "+plugin.getDeclaringPluginDescriptor().getId()
							+", plugin not found or can't be activated",e);
				}
				throw e;
			}
		}
		throw new IllegalArgumentException("Requested plugin "+desiredPlugin+" implementing extension point "+extName
				+" in plugin "+parentPluginName+" could not be found");
	}

	/**
	 * Interrogates the plugin framework to determine which plugin an object belongs to.
	 * 
	 * @param  obj
	 *         The object to check for an owning plugin for
	 *
	 * @return A <tt>String</tt> containing the name of the plugin the object belongs to
	 *         or an empty <tt>String</tt> if the class defining the object was loaded outside
	 *         of the plugin framework.
	 */
	public static String getPluginIdForObject(Object obj) {
		String returnId = "";
		PluginManager manager = PluginManager.lookup(obj);
		if (manager != null) {
			Plugin plugin = manager.getPluginFor(obj);
			if (plugin != null) {
				returnId = plugin.getDescriptor().getId();
			}
		}
		return returnId;
	}
}
