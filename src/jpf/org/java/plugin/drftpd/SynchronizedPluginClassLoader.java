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
package org.java.plugin.drftpd;

import org.java.plugin.PluginManager;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.standard.StandardPluginClassLoader;

/**
 * Synchronized implementation of the standard plug-in class loader.
 * @see org.java.plugin.standard.StandardPluginClassLoader
 * 
 * @author djb61
 * @version $Id$
 */
public class SynchronizedPluginClassLoader extends StandardPluginClassLoader {

	private static final Object MUTEX = new Object();

	/**
	 * Creates class instance configured to load classes and resources for given
	 * plug-in.
	 * @see org.java.plugin.standard.StandardPluginClassLoader#StandardPluginClassLoader(
	 * PluginManager, PluginDescriptor, ClassLoader)
	 * 
	 * @param aManager
	 *            plug-in manager instance
	 * @param descr
	 *            plug-in descriptor
	 * @param parent
	 *            parent class loader, usually this is JPF "host" application
	 *            class loader
	 */
	public SynchronizedPluginClassLoader(final PluginManager aManager,
			final PluginDescriptor descr, final ClassLoader parent) {
		super(aManager, descr, parent);
	}

	/**
	 * @see java.lang.ClassLoader#loadClass(java.lang.String, boolean)
	 */
	@Override
	protected Class<?> loadClass(final String name, final boolean resolve) throws ClassNotFoundException {
		Class<?> rtnClass;
		synchronized(MUTEX) {
			rtnClass = super.loadClass(name,resolve);
		}
		return rtnClass;
	}

	/**
	 * @see org.java.plugin.standard.StandardPluginClassLoader#setProbeParentLoaderLast(
	 *      boolean)
	 */
	@Override
	protected void setProbeParentLoaderLast(final boolean value) {
		super.setProbeParentLoaderLast(value);
	}

	/**
	 * @see org.java.plugin.standard.StandardPluginClassLoader#setStickySynchronizing(
	 *      boolean)
	 */
	@Override
	protected void setStickySynchronizing(final boolean value) {
		super.setStickySynchronizing(value);
	}

	/**
	 * @see org.java.plugin.standard.StandardPluginClassLoader#setLocalClassLoadingOptimization(
	 *      boolean)
	 */
	@Override
	protected void setLocalClassLoadingOptimization(final boolean value) {
		super.setLocalClassLoadingOptimization(value);
	}

	/**
	 * @see org.java.plugin.standard.StandardPluginClassLoader#setForeignClassLoadingOptimizationt(
	 *      boolean)
	 */
	@Override
	protected void setForeignClassLoadingOptimization(final boolean value) {
		super.setForeignClassLoadingOptimization(value);
	}
}
