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

import org.java.plugin.Plugin;
import org.java.plugin.PluginLifecycleException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

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

	private static final Logger logger = LogManager.getLogger(CommonPluginUtils.class);

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
	 * @return  A <tt>List</tt> containing an instance of the class from each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<T> getPluginObjects(Object caller, String pluginName, String extName, String classParamName) 
	throws IllegalArgumentException {
		List<T> objList = null;
		try {
			objList = getPluginObjects(caller, pluginName, extName, classParamName, null, 
					null, true, true, false);
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
		return objList;
	}

	/**
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt>.
	 * Each plugin which implements the extension point will be activated in the plugin
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
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @return  A <tt>List</tt> containing an instance of the class from each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<T> getPluginObjects(Object caller, String pluginName, String extName, String classParamName,
			Class<?>[] constructorSig, Object[] constructorArgs) 
			throws IllegalArgumentException {
		List<T> objList = null;
		try {
			objList = getPluginObjects(caller, pluginName, extName, classParamName, constructorSig, 
					constructorArgs, true, true, false);
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
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in a loaded class and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and a loaded class has no such constructor and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  PluginLifecycleException
	 *          If a plugin cannot be activated and <tt>failOnError</tt> is <tt>true</tt>
	 */
	public static <T> List<T> getPluginObjects(Object caller, String pluginName, String extName, String classParamName,
			Class<?>[] constructorSig, Object[] constructorArgs, boolean activatePlugin, boolean logError, boolean failOnError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		List<T> pluginObjs = new ArrayList<>();
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					pluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				if (activatePlugin && !manager.isPluginActivated(plugin.getDeclaringPluginDescriptor())) {
					manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
				}
				ClassLoader pluginLoader = manager.getPluginClassLoader( 
						plugin.getDeclaringPluginDescriptor());
				Class<T> pluginCls = loadPluginClass(pluginLoader, 
						plugin.getParameter(classParamName).valueAsString());
				if (constructorSig == null) {
					pluginObjs.add(pluginCls.getDeclaredConstructor().newInstance());
				} else {
					pluginObjs.add(pluginCls.getConstructor(constructorSig).newInstance(constructorArgs));
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
		T retObj = null;
		try {
			retObj = CommonPluginUtils.getSinglePluginObject(caller, parentPluginName, extName, classParamName, desiredPlugin,
					null, null, true, true);
		} catch (InvocationTargetException e) {
			// can't happen as called with nullary constructor
		} catch (NoSuchMethodException e) {
			// can't happen as called with nullary constructor
		}
		return retObj;
	}

	/**
	 * Get an instance of a plugin class extending the given parent plugin at the given extension point provided
	 * by the given plugin.
	 * If the child plugin is not activated in the plugin framework then it will be activated
	 * by this method, if any error is encountered loading the desired plugin then this will be logged to the standard
	 * log files and an exception thrown. This method should be used when the class to be loaded needs to be instantiated
	 * using a non nullary constructor.
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
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in the loaded class
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and the loaded class has no such constructor
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	public static <T> T getSinglePluginObject(Object caller, String parentPluginName, String extName, String classParamName,
			String desiredPlugin, Class<?>[] constructorSig, Object[] constructorArgs) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		return CommonPluginUtils.getSinglePluginObject(caller, parentPluginName, extName, classParamName, desiredPlugin,
				constructorSig, constructorArgs, true, true);
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
	 * @param  constructorSig
	 *         The signature of the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
	 *
	 * @param  constructorArgs
	 *         The objects to pass to the constructor in the class to be used when instantiating an instance.
	 *         To use an empty constructor pass <tt>null</tt>.
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in the loaded class
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and the loaded class has no such constructor
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	public static <T> T getSinglePluginObject(Object caller, String parentPluginName, String extName, String classParamName,
			String desiredPlugin, Class<?>[] constructorSig, Object[] constructorArgs, boolean activatePlugin, boolean logError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					parentPluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				if (plugin.getDeclaringPluginDescriptor().getId().equals(desiredPlugin)) {
					if (activatePlugin && !manager.isPluginActivated(plugin.getDeclaringPluginDescriptor())) {
						manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
					}
					ClassLoader pluginLoader = manager.getPluginClassLoader( 
							plugin.getDeclaringPluginDescriptor());
					Class<T> pluginCls = loadPluginClass(pluginLoader, 
							plugin.getParameter(classParamName).valueAsString());
					if (constructorSig == null) {
						return pluginCls.getDeclaredConstructor().newInstance();
					}
					return pluginCls.getConstructor(constructorSig).newInstance(constructorArgs);
				}
			} catch (ClassNotFoundException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
				}
				throw e;
			}catch (IllegalAccessException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} has no default constructor", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
				}
				throw e;
			} catch (InstantiationException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} is not a concrete class", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
				}
				throw e;
			} catch (InvocationTargetException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested constructor in class {} threw an exception", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
				}
				throw e;
			} catch (NoSuchMethodException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested constructor in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), plugin.getParameter(classParamName).valueAsString(), e);
				}
				throw e;
			}  catch (PluginLifecycleException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, plugin not found or can't be activated", plugin.getDeclaringPluginDescriptor().getId(), e);
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
	 * @return  A <tt>String</tt> containing the name of the plugin the object belongs to
	 *          or an empty <tt>String</tt> if the class defining the object was loaded outside
	 *          of the plugin framework.
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

	/**
	 * Interrogates the plugin framework to get the version number of the plugin which loaded the object.
	 * 
	 * @param  obj
	 *         The object to check for an owning plugin for
	 *
	 * @return  A <tt>String</tt> containing the version number of the plugin the object belongs to
	 *          or an empty <tt>String</tt> if the class defining the object was loaded outside
	 *          of the plugin framework.
	 */
	public static String getPluginVersionForObject(Object obj) {
		String returnId = "";
		PluginManager manager = PluginManager.lookup(obj);
		if (manager != null) {
			Plugin plugin = manager.getPluginFor(obj);
			if (plugin != null) {
				returnId = plugin.getDescriptor().getVersion().toString();
			}
		}
		return returnId;
	}

	/**
	 * Loads the given class using the given classloader.
	 * 
	 * @param  loader
	 *         The classloader to use to load the class with
	 *
	 * @param  className
	 *         The name of the class to load
	 *
	 * @throws  ClassNotFoundException
	 *          If the requested class cannot be found by the provided classloader
	 *
	 * @return  A <tt>Class</tt> object of the loaded class
	 */
	@SuppressWarnings("unchecked")
	protected static <T> Class<T> loadPluginClass(ClassLoader loader, String className) throws ClassNotFoundException {
		return (Class<T>)loader.loadClass(className);
	}

	/**
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
	 *          extension that could successfully be loaded from.
	 *
	 * @throws  IllegalArgumentException
	 *          If the requested extension point does not exist
	 */
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, null,
					null, null, null, null, null, true, true, true, false);
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
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, boolean createInstance) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, null,
					null, null, null, null, null, createInstance, true, true, false);
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
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					null, null, null, null, methodSig, true, true, true, false);
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
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, Class<?>[] constructorSig, Object[] constructorArgs, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					null, null, constructorSig, constructorArgs, methodSig, true, true, true, false);
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
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, String inclusionParamName, String inclusionValue, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					inclusionParamName, inclusionValue, null, null, methodSig, true, true, true, false);
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
	 * Get an instance of each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method.
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
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, String inclusionParamName, String inclusionValue,
			Class<?>[] constructorSig, Object[] constructorArgs, Class<?>[] methodSig) 
			throws IllegalArgumentException {
		List<PluginObjectContainer<T>> containerList = null;
		try {
			containerList = getPluginObjectsInContainer(caller, pluginName, extName, classParamName, methodParamName,
					inclusionParamName, inclusionValue, constructorSig, constructorArgs, methodSig, true, true, true, false);
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
	 * Get each plugin class extending the extension point as a <tt>List</tt> of
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the containers will contain an instance of the method. If an instance of the
	 * class has been requested this will be in the container.
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
	 * @return  A <tt>List</tt> containing a <tt>PluginObjectContainer</tt> for each 
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in a loaded class and
	 *          <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and a loaded class has no such constructor and
	 *          <tt>failOnError</tt> is <tt>true</tt>. Also can be thrown if a method is requested, the
	 *          method cannot be found in the class and <tt>failOnError</tt> is <tt>true</tt>
	 *
	 * @throws  PluginLifecycleException
	 *          If a plugin cannot be activated and <tt>failOnError</tt> is <tt>true</tt>
	 */
	public static <T> List<PluginObjectContainer<T>> getPluginObjectsInContainer(Object caller, String pluginName, String extName,
			String classParamName, String methodParamName, String inclusionParamName, String inclusionParamValue,
			Class<?>[] constructorSig, Object[] constructorArgs, Class<?>[] methodSig, boolean createInstance,
			boolean activatePlugin, boolean logError, boolean failOnError) throws ClassNotFoundException, IllegalAccessException,
			IllegalArgumentException, InstantiationException, InvocationTargetException, NoSuchMethodException,
			PluginLifecycleException {
		List<PluginObjectContainer<T>> pluginContainers = new ArrayList<>();
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					pluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
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
				pluginContainers.add(container);
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
		return pluginContainers;
	}

	/**
	 * Get the plugin class extending the extension point as wrapped in a
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the container will contain an instance of the method.
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
	 * @param  className
	 *         The name of the class instantiate/load
	 *
	 * @param  methodName
	 *         The name of the method to instantiate (Optional)
	 *
	 * @param  desiredPlugin
	 *         The name of the plugin providing the extension to be loaded
	 *
	 * @param  methodSig
	 *         The signature of the method in the class to instantiate a method instance for.
	 *         If no method instance is required pass <tt>null</tt>.
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in the loaded class
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and the loaded class has no such constructor
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	public static <T> PluginObjectContainer<T> getSinglePluginObjectInContainer(Object caller, String parentPluginName, String extName,
			String className, String methodName, String desiredPlugin, Class<?>[] methodSig) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		return getSinglePluginObjectInContainer(caller, parentPluginName, extName, className, methodName, desiredPlugin,
				null, null, methodSig, true, true, true);
	}

	/**
	 * Get the plugin class extending the extension point as wrapped in a
	 * {@link org.drftpd.util.PluginObjectContainer PluginObjectContainer}. If a method signature has
	 * been passed then the container will contain an instance of the method. If an instance of the
	 * class has been requested this will be in the container.
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
	 * @param  className
	 *         The name of the class instantiate/load
	 *
	 * @param  methodName
	 *         The name of the method to instantiate (Optional)
	 *
	 * @param  desiredPlugin
	 *         The name of the plugin providing the extension to be loaded
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
	 *         If <tt>true</tt> then the requested plugin will be activated in the plugin framework if it
	 *         is not already active.
	 *
	 * @param  logError
	 *         If <tt>true</tt> then any errors encounted whilst loading the requested plugin will be logged to the
	 *         standard log files.
	 *
	 * @return  A container with a <tt>Class</tt> object and the <tt>Extension</tt> object from which it was loaded.
	 *          Optionally the container may also contain an object instance of the class and a method instance.
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
	 * @throws  InvocationTargetException
	 *          If an exception is thrown from a requested non-default constructor in the loaded class
	 *
	 * @throws  NoSuchMethodException
	 *          If a non-default constructor is requested and a loaded class has no such constructor.
	 *          Also can be thrown if a method is requested and method cannot be found in the class.
	 *
	 * @throws  PluginLifecycleException
	 *          If the requested plugin cannot be activated
	 */
	public static <T> PluginObjectContainer<T> getSinglePluginObjectInContainer(Object caller, String parentPluginName,
			String extName, String className, String methodName, String desiredPlugin, Class<?>[] constructorSig,
			Object[] constructorArgs, Class<?>[] methodSig, boolean createInstance, boolean activatePlugin, boolean logError) throws
			ClassNotFoundException, IllegalAccessException, IllegalArgumentException, InstantiationException,
			InvocationTargetException, NoSuchMethodException, PluginLifecycleException {
		PluginManager manager = PluginManager.lookup(caller);
		ExtensionPoint pluginExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					parentPluginName, extName);
		for (Extension plugin : pluginExtPoint.getConnectedExtensions()) {
			try {
				if (plugin.getDeclaringPluginDescriptor().getId().equals(desiredPlugin)) {
					if (activatePlugin && !manager.isPluginActivated(plugin.getDeclaringPluginDescriptor())) {
						manager.activatePlugin(plugin.getDeclaringPluginDescriptor().getId());
					}
					ClassLoader pluginLoader = manager.getPluginClassLoader( 
							plugin.getDeclaringPluginDescriptor());
					Class<T> pluginCls = loadPluginClass(pluginLoader,className); 
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
								Method pluginMethod = pluginCls.getMethod(methodName, methodSig);
								container = new PluginObjectContainer<>(pluginCls, pluginInstance, plugin, pluginMethod);
							} catch (NoSuchMethodException e) {
								if (logError) {
                                    logger.warn("Error loading plugin {}, requested method {} in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), methodName, className, e);
								}
								throw e;
							}
						}
					} else {
						container = new PluginObjectContainer<>(pluginCls, plugin);
					}
					return container;
				}
			} catch (ClassNotFoundException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} not found", plugin.getDeclaringPluginDescriptor().getId(), className, e);
				}
				throw e;
			}catch (IllegalAccessException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} has no default constructor", plugin.getDeclaringPluginDescriptor().getId(), className, e);
				}
				throw e;
			} catch (InstantiationException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested class {} is not a concrete class", plugin.getDeclaringPluginDescriptor().getId(), className, e);
				}
				throw e;
			} catch (InvocationTargetException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested constructor in class {} threw an exception", plugin.getDeclaringPluginDescriptor().getId(), className, e);
				}
				throw e;
			} catch (NoSuchMethodException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, requested constructor in class {} not found", plugin.getDeclaringPluginDescriptor().getId(), className, e);
				}
				throw e;
			}  catch (PluginLifecycleException e) {
				if (logError) {
                    logger.warn("Error loading plugin {}, plugin not found or can't be activated", plugin.getDeclaringPluginDescriptor().getId(), e);
				}
				throw e;
			}
		}
		throw new IllegalArgumentException("Requested plugin "+desiredPlugin+" implementing extension point "+extName
				+" in plugin "+parentPluginName+" could not be found");
	}
}
