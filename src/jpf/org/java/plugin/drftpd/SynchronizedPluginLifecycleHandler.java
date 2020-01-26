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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.standard.StandardPluginLifecycleHandler;
import org.java.plugin.util.ExtendedProperties;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Standard implementation of plug-in life cycle handler.
 * 
 * Creates class loaders for plugins which are synchronized using a global mutex, this
 * makes all class loading from plugin framework loaders single threaded.
 * @see org.java.plugin.standard.StandardPluginLifecycleHandler
 * 
 * @author djb61
 * @version $Id$
 */
public class SynchronizedPluginLifecycleHandler extends StandardPluginLifecycleHandler {

	private final Logger logger = LogManager.getLogger(getClass());
	private boolean probeParentLoaderLast;
	private boolean stickySynchronizing;
	private boolean localClassLoadingOptimization;
	private boolean foreignClassLoadingOptimization;

	/**
	 * Creates synchronized implementation of plug-in class loader.
	 * @see org.java.plugin.standard.StandardPluginLifecycleHandler#createPluginClassLoader(
	 *      org.java.plugin.registry.PluginDescriptor)
	 */
	@Override
	protected org.java.plugin.PluginClassLoader createPluginClassLoader(
			final PluginDescriptor descr) {
		SynchronizedPluginClassLoader result;
		if (System.getSecurityManager() != null) {
			result = AccessController.doPrivileged(
                    (PrivilegedAction<SynchronizedPluginClassLoader>) () -> new SynchronizedPluginClassLoader(getPluginManager(), descr,
                            SynchronizedPluginLifecycleHandler.this.getClass()
                            .getClassLoader()));
		} else {
			result = new SynchronizedPluginClassLoader(getPluginManager(), descr,
					SynchronizedPluginLifecycleHandler.this.getClass()
					.getClassLoader());
		}
		result.setProbeParentLoaderLast(probeParentLoaderLast);
		result.setStickySynchronizing(stickySynchronizing);
		result.setLocalClassLoadingOptimization(localClassLoadingOptimization);
		result.setForeignClassLoadingOptimization(foreignClassLoadingOptimization);

		return result;
	}

	/**
	 * @see org.java.plugin.standard.StandardPluginLifecycleHandler#configure(
	 *      ExtendedProperties)
	 */
	@Override
	public void configure(ExtendedProperties config) {
		probeParentLoaderLast = "true".equalsIgnoreCase( //$NON-NLS-1$
				config.getProperty("probeParentLoaderLast", "false")); //$NON-NLS-1$ //$NON-NLS-2$
        logger.debug("probeParentLoaderLast parameter value is {}", probeParentLoaderLast);
		stickySynchronizing = "true".equalsIgnoreCase( //$NON-NLS-1$
				config.getProperty("stickySynchronizing", "false")); //$NON-NLS-1$ //$NON-NLS-2$
        logger.debug("stickySynchronizing parameter value is {}", stickySynchronizing);
		localClassLoadingOptimization = !"false".equalsIgnoreCase( //$NON-NLS-1$
				config.getProperty("localClassLoadingOptimization", //$NON-NLS-1$
				"true")); //$NON-NLS-1$
        logger.debug("localLoadingClassOptimization parameter value is {}", localClassLoadingOptimization);
		foreignClassLoadingOptimization = !"false".equalsIgnoreCase( //$NON-NLS-1$
				config.getProperty("foreignClassLoadingOptimization", //$NON-NLS-1$
				"true")); //$NON-NLS-1$
        logger.debug("foreignClassLoadingOptimization parameter value is {}", foreignClassLoadingOptimization);
	}
}
