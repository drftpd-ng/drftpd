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

import org.java.plugin.registry.Extension;

import java.lang.reflect.Method;

/**
 * A wrapper object used to contain a class loaded by the plugin framework and optionally
 * an instance of the class and a method obtained by reflection. The container also 
 * contains the <tt>Extension</tt> object from which the class was loaded.
 * 
 * @author djb61
 * @version $Id$
 */
public class PluginObjectContainer<T> {

	private Class<T> _pluginClass;
	private T _pluginObject;
	private Method _pluginMethod;
	private Extension _pluginExtension;

	/**
	 * Creates a new <tt>PluginObjectContainer</tt> containing only the <tt>Class</tt>
	 * loaded from the framework and the <tt>Extension</tt> from which it was loaded.
	 * 
	 * @param  pluginClass
	 *         The <tt>Class</tt> object obtained from the plugin framework.
	 *
	 * @param  pluginExtension
	 *         The <tt>Extension</tt> object which was used to load <tt>pluginClass</tt>
	 */
	public PluginObjectContainer(Class<T> pluginClass, Extension pluginExtension) {
		_pluginClass = pluginClass;
		_pluginExtension = pluginExtension;
	}

	/**
	 * Creates a new <tt>PluginObjectContainer</tt> containing the <tt>Class</tt>
	 * loaded from the framework and an instance of the <tt>Class</tt> as well as
	 * the <tt>Extension</tt> from which it was loaded.
	 * 
	 * @param  pluginClass
	 *         The <tt>Class</tt> object obtained from the plugin framework.
	 *
	 * @param  pluginObject
	 *         An instance of <tt>pluginClass</tt>
	 *
	 * @param  pluginExtension
	 *         The <tt>Extension</tt> object which was used to load <tt>pluginClass</tt>
	 */
	public PluginObjectContainer(Class<T> pluginClass, T pluginObject, Extension pluginExtension) {
		_pluginClass = pluginClass;
		_pluginObject = pluginObject;
		_pluginExtension = pluginExtension;
	}

	/**
	 * Creates a new <tt>PluginObjectContainer</tt> containing the <tt>Class</tt>
	 * loaded from the framework and an instance of the <tt>Class</tt> as well as
	 * the <tt>Extension</tt> from which it was loaded. One of the methods belonging
	 * to the <tt>Class</tt> will also be stored in the container.
	 * 
	 * @param  pluginClass
	 *         The <tt>Class</tt> object obtained from the plugin framework.
	 *
	 * @param  pluginObject
	 *         An instance of <tt>pluginClass</tt>
	 *
	 * @param  pluginExtension
	 *         The <tt>Extension</tt> object which was used to load <tt>pluginClass</tt>
	 *
	 * @param  pluginMethod
	 *         A <tt>Method</tt> instance of one of the methods belonging to <tt>pluginClass</tt>
	 */
	public PluginObjectContainer(Class<T> pluginClass, T pluginObject, Extension pluginExtension, Method pluginMethod) {
		_pluginClass = pluginClass;
		_pluginObject = pluginObject;
		_pluginExtension = pluginExtension;
		_pluginMethod = pluginMethod;
	}

	/**
	 * Returns the <tt>Class</tt> object
	 *
	 * @return  The <tt>Class</tt> object
	 */
	public Class<T> getPluginClass() {
		return _pluginClass;
	}

	/**
	 * Returns the instance of the contained <tt>Class</tt> object or
	 * null if no instance was stored for the class.
	 * 
	 * @return  An instance of the <tt>Class</tt> object or <tt>null</tt>
	 */
	public T getPluginObject() {
		return _pluginObject;
	}

	/**
	 * Returns an instance of a method belonging to the contained <tt>Class</tt>
	 * object or null if no method instance was stored for the class.
	 * 
	 * @return  A method instance belonging to the <tt>Class</tt> object or <tt>null</tt>
	 */
	public Method getPluginMethod() {
		return _pluginMethod;
	}

	/**
	 * Returns the <tt>Extension</tt> object from which the container contents were obtained
	 *
	 * @return  The <tt>Extension</tt> object
	 */
	public Extension getPluginExtension() {
		return _pluginExtension;
	}
}
