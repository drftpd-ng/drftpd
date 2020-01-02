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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * A collection of helper methods to perform common tasks with the plugin framework.
 * This class provides static methods to perform operations using the underlying plugin
 * framework which are commonly required when writing new plugins. Use of these methods
 * abstracts away much of the work with the framework API to allow use of this functionality
 * without needing an indepth understanding of how the plugin framework operates.
 * The methods in this class are for operations only applicable to the master process, for
 * more general methods see {@link org.drftpd.util.CommonPluginUtils CommonPluginUtils}
 * 
 * @see org.drftpd.util.CommonPluginUtils
 * 
 * @author djb61
 * @version $Id$
 */
public class MasterPluginUtils extends CommonPluginUtils {

	private static final Logger logger = LogManager.getLogger(MasterPluginUtils.class);

	/**
	 * Get a <tt>Set</tt> containing the currently loaded extensions which belong to the unloaded plugin.
	 * 
	 * @param  caller
	 *         The object instance calling this method
	 *
	 * @param  extName
	 *         The name of the extension point in the plugin to get plugin objects for
	 *
	 * @param  event
	 *         The <tt>UnloadPluginEvent</tt> event object containing details of the unloaded plugin
	 *
	 * @param  loadedExtensions
	 *         A <tt>Collection</tt> containing the object instances currently held for all loaded plugins
	 *         against the extension point.
	 *
	 * @return  A <tt>Set</tt> containing the object instances from the <tt>loadedExtensions</tt> <tt>List</tt>
	 *          which belong to the plugin being unloaded. If the unloading plugin does not implement the
	 *          extension point then an empty <tt>Set</tt> is returned.
	 */
	public static <T> Set<T> getUnloadedExtensionObjects(Object caller, String extName, UnloadPluginEvent event, Collection<T> loadedExtensions) {
		Set<T> unloadedExtensions = new HashSet<>();
		PluginManager manager = PluginManager.lookup(caller);
		String currentPlugin = manager.getPluginFor(caller).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin) && extension.equals(extName)) {
                for (T extObj : loadedExtensions) {
                    if (manager.getPluginFor(extObj).getDescriptor().getId().equals(event.getPlugin())) {
                        unloadedExtensions.add(extObj);
                    }
                }
			}
		}
		return unloadedExtensions;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt>.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>.
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
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @return  A <tt>List</tt> containing an instance of the class from each extension that could
	 *          successfully be loaded from. If the plugin being loaded does not implement the passed
	 *          extension point then an empty <tt>List</tt> will be returned.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<T> getLoadedExtensionObjects(Object caller, String parentPluginName, String extName, 
			String classParamName, LoadPluginEvent event) throws IllegalArgumentException {
		List<T> loadedExtensions = null;
		try {
			loadedExtensions = getLoadedExtensionObjects(caller, parentPluginName, extName, classParamName, event,
					null, null, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return loadedExtensions;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt>.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing an instance of the class from each extension that could
	 *          successfully be loaded from. If the plugin being loaded does not implement the passed
	 *          extension point then an empty <tt>List</tt> will be returned.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<T> getLoadedExtensionObjects(Object caller, String parentPluginName, String extName, 
			String classParamName, LoadPluginEvent event, Class<?>[] constructorSig, Object[] constructorArgs)
			throws IllegalArgumentException {
		List<T> loadedExtensions = null;
		try {
			loadedExtensions = getLoadedExtensionObjects(caller, parentPluginName, extName, classParamName, event,
					constructorSig, constructorArgs, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return loadedExtensions;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt>.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  activatePlugin
	 *         If <tt>true</tt> then the new plugin will be activated in the plugin framework if it
	 *         is not already active and it implements the passed extension point.
	 *
	 * @param  logError
	 *         If <tt>true</tt> then any errors encounted whilst loading plugins will be logged to the
	 *         standard log files.
	 *
	 * @param  failOnError
	 *         If <tt>true</tt> then the method will fail and throw an exception if loading a plugin fails.
	 *
	 * @return  A <tt>List</tt> containing an instance of the class from each extension that could
	 *          successfully be loaded from. If the plugin being loaded does not implement the passed
	 *          extension point then an empty <tt>List</tt> will be returned.
	 *
	 * @throws  ClassNotFoundException
	 *          If the class in the extension definition of the plugin cannot be found and <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  IllegalAccessException
	 *          If the class in the extension definition of the plugin has no default constructor and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 *
	 * @throws  InstantiationException
	 *          If the class in the extension definition of the plugin is not a concrete class and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in a loaded class and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and a loaded class has no such constructor and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  PluginLifecycleException
	 *          If the plugin cannot be activated and <tt>failOnError</tt> is <tt>true</tt>
	 */
	public static <T> List<T> getLoadedExtensionObjects(Object caller, String parentPluginName, String extName, 
			String classParamName, LoadPluginEvent event, Class<?>[] constructorSig, Object[] constructorArgs,
			boolean activatePlugin, boolean logError, boolean failOnError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		List<T> loadedExtensions = new ArrayList<>();
		PluginManager manager = PluginManager.lookup(caller);
		String currentPlugin = manager.getPluginFor(caller).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin) && extension.equals(extName)) {
				ExtensionPoint pluginExtPoint = 
					manager.getRegistry().getExtensionPoint( 
							parentPluginName, extName);
				for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
					if (plugin.getDeclaringPluginDescriptor().getId().equals(event.getPlugin())) {
						try {
							if (activatePlugin && !manager.isPluginActivated(plugin.getDeclaringPluginDescriptor())) {
								manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
							}
							ClassLoader pluginLoader = manager.getPluginClassLoader( 
									plugin.getDeclaringPluginDescriptor());
							Class<T> pluginCls = loadPluginClass(pluginLoader,
									plugin.getParameter(classParamName).valueAsString());
							if (constructorSig == null) {
								loadedExtensions.add(pluginCls.getDeclaredConstructor().newInstance());
							} else {
								loadedExtensions.add(pluginCls.getConstructor(constructorSig).newInstance(constructorArgs));
							}
						} catch (ClassNotFoundException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (IllegalAccessException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} has no default constructor", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (InstantiationException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} is not a concrete class", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (InvocationTargetException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested constructor in class {} threw an exception", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (NoSuchMethodException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested constructor in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (PluginLifecycleException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, plugin not found or can't be activated", plugin.getDeclaringPluginDescriptor().getId(), e);
							}
							if (failOnError) {
								throw e;
							}
						}
					}
				}
			}
		}
		return loadedExtensions;
	}
	
	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String parentPluginName,
			String extName, LoadPluginEvent event, String classParamName) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, parentPluginName, extName, classParamName, null,
					null, null, event, null, null, null, true, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *         
	 * @param  createInstance
	 *         If <tt>false</tt> then no instance of the loaded class will be returned in the container.
	 *         This is useful if the class to be loaded is abstract or otherwise cannot be instantiated.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, LoadPluginEvent event, boolean createInstance) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, pluginName, extName, classParamName, null,
					null, null, event, null, null, null, createInstance, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  methodParamName
	 *         The name of the parameter in the extension point containing the method name to instantiate (Optional)
	 *
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, LoadPluginEvent event, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					null, null, event, null, null, methodSig, true, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  methodParamName
	 *         The name of the parameter in the extension point containing the method name to instantiate (Optional)
	 *
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, LoadPluginEvent event, Class<?>[] constructorSig,
			Object[] constructorArgs, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					null, null, event, constructorSig, constructorArgs, methodSig, true, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  methodParamName
	 *         The name of the parameter in the extension point containing the method name to instantiate (Optional)
	 *
	 * @param  inclusionParamName
	 *         The name of the parameter in the extension point whose value to check for inclusion (Optional)
	 *         If this argument is <tt>null</tt> then all extensions will be returned
	 *
	 * @param  inclusionValue
	 *         The string to compare against the contents of the parameter name passed in <tt>inclusionParamName</tt> (Optional)
	 *         If <tt>inclusionParamName</tt> is not <tt>null</tt> then a value must be provided here.
	 *
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, String inclusionParamName, String inclusionValue,
			LoadPluginEvent event, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					inclusionParamName, inclusionValue, event, null, null, methodSig, true, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}

	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  methodParamName
	 *         The name of the parameter in the extension point containing the method name to instantiate (Optional)
	 *
	 * @param  inclusionParamName
	 *         The name of the parameter in the extension point whose value to check for inclusion (Optional)
	 *         If this argument is <tt>null</tt> then all extensions will be returned
	 *
	 * @param  inclusionValue
	 *         The string to compare against the contents of the parameter name passed in <tt>inclusionParamName</tt> (Optional)
	 *         If <tt>inclusionParamName</tt> is not <tt>null</tt> then a value must be provided here.
	 *
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, String inclusionParamName, String inclusionValue,
			LoadPluginEvent event, Class<?>[] constructorSig, Object[] constructorArgs, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getLoadedExtensionObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					inclusionParamName, inclusionValue, event, constructorSig, constructorArgs, methodSig, true, true, true, false);
		} catch (PluginLifecycleException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (ClassNotFoundException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (IllegalAccessException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InstantiationException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (InvocationTargetException e) {
			// Can't happen as method has been called with argument not to throw this exception
		} catch (NoSuchMethodException e) {
			// Can't happen as method has been called with argument not to throw this exception
		}
		return containerList;
	}
	
	/**
	 * Get an instance of each newly loaded plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}.
	 * Each new plugin which implements the extension point will be activated in the plugin
	 * framework if it is not already active. Any errors encounted when loading plugins
	 * will be logged to the standard log files, this method will not fail if loading one
	 * or more plugins fails, the failed plugins will not be contained in the returned
	 * <tt>List</tt>. This method should be used when the classes to be loaded require
	 * a non nullary constructor to be used at instantiation.
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
	 * @param  methodParamName
	 *         The name of the parameter in the extension point containing the method name to instantiate (Optional)
	 *
	 * @param  inclusionParamName
	 *         The name of the parameter in the extension point whose value to check for inclusion (Optional)
	 *         If this argument is <tt>null</tt> then all extensions will be returned
	 *
	 * @param  inclusionValue
	 *         The string to compare against the contents of the parameter name passed in <tt>inclusionParamName</tt> (Optional)
	 *         If <tt>inclusionParamName</tt> is not <tt>null</tt> then a value must be provided here.
	 *
	 * @param  event
	 *         The <tt>LoadPluginEvent</tt> event object containing details of the newly loaded plugin
	 *
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
	 *
	 * @param  createInstance
	 *         If <tt>true</tt> then an instance of the loaded class will be returned in the container.
	 *         This must be <tt>true</tt> if a method instance is required.
	 *
	 * @param  activatePlugin
	 *         If <tt>true</tt> then the new plugin will be activated in the plugin framework if it
	 *         is not already active and it implements the passed extension point.
	 *
	 * @param  logError
	 *         If <tt>true</tt> then any errors encounted whilst loading plugins will be logged to the
	 *         standard log files.
	 *
	 * @param  failOnError
	 *         If <tt>true</tt> then the method will fail and throw an exception if loading a plugin fails.
	 *
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each each extension that could
	 *          successfully be loaded from. If the plugin being loaded does not implement the passed
	 *          extension point then an empty <tt>List</tt> will be returned.
	 *
	 * @throws  ClassNotFoundException
	 *          If the class in the extension definition of the plugin cannot be found and <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  IllegalAccessException
	 *          If the class in the extension definition of the plugin has no default constructor and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 *
	 * @throws  InstantiationException
	 *          If the class in the extension definition of the plugin is not a concrete class and <tt>failOnError</tt>
	 *          is <tt>true</tt>
	 *
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in a loaded class and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and a loaded class has no such constructor and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  PluginLifecycleException
	 *          If the plugin cannot be activated and <tt>failOnError</tt> is <tt>true</tt>
	 */
	public static <T> List<PluginObjectContainer<T>> getLoadedExtensionObjectsInContainer(Object caller, String parentPluginName, String extName, 
			String classParamName, String methodParamName, String inclusionParamName, String inclusionParamValue,
			LoadPluginEvent event, Class<?>[] constructorSig, Object[] constructorArgs, Class<?>[] methodSig,
			boolean createInstance, boolean activatePlugin, boolean logError, boolean failOnError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		List<PluginObjectContainer<T>> loadedExtensions = new ArrayList<>();
		PluginManager manager = PluginManager.lookup(caller);
		String currentPlugin = manager.getPluginFor(caller).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin) && extension.equals(extName)) {
				ExtensionPoint pluginExtPoint = 
					manager.getRegistry().getExtensionPoint( 
							parentPluginName, extName);
				for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
					if (plugin.getDeclaringPluginDescriptor().getId().equals(event.getPlugin())) {
						if (inclusionParamName != null && !plugin.getParameter(inclusionParamName).valueAsString().equals(inclusionParamValue)) {
							continue;
						}
						try {
							if (activatePlugin && !manager.isPluginActivated(plugin.getDeclaringPluginDescriptor())) {
								manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
							}
							ClassLoader pluginLoader = manager.getPluginClassLoader( 
									plugin.getDeclaringPluginDescriptor());
							Class<T> pluginCls = loadPluginClass(pluginLoader,
									plugin.getParameter(classParamName).valueAsString());
							PluginObjectContainer<T> container = null;
							if (createInstance) {
								T pluginInstance = null;
								if (constructorSig == null) {
									pluginInstance = pluginCls.getDeclaredConstructor().newInstance();
								} else {
									pluginInstance = pluginCls.getConstructor(constructorSig).newInstance(constructorArgs);
								}
								if (methodSig == null) {
									container = new PluginObjectContainer<>(pluginCls, pluginInstance, plugin);
								} else {
									try {
										Method pluginMethod = pluginCls.getMethod(plugin.getParameter(methodParamName).valueAsString()
												, methodSig);
										container = new PluginObjectContainer<>(pluginCls, pluginInstance, plugin, pluginMethod);
									} catch (NoSuchMethodException e) {
										if (logError) {
                                            logger.warn("Error loading plugin {}, requested method {} in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(methodParamName).valueAsString(), plugin.getParameter(classParamName).valueAsString(), e);
										}
										if (failOnError) {
											throw e;
										}
										continue;
									}
								}
							} else {
								container = new PluginObjectContainer<>(pluginCls, plugin);
							}
							loadedExtensions.add(container);
						} catch (ClassNotFoundException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (IllegalAccessException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} has no default constructor", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (InstantiationException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested class {} is not a concrete class", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (InvocationTargetException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested constructor in class {} threw an exception", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (NoSuchMethodException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, requested constructor in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
							}
							if (failOnError) {
								throw e;
							}
						} catch (PluginLifecycleException e) {
							if (logError) {
                                logger.warn("Error loading plugin {}, plugin not found or can't be activated", plugin.getDeclaringPluginDescriptor().getId(), e);
							}
							if (failOnError) {
								throw e;
							}
						}
					}
				}
			}
		}
		return loadedExtensions;
	}
}
