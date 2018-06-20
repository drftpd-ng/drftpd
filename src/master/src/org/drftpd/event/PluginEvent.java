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
package org.drftpd.event;

import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;

import java.util.ArrayList;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginEvent {

	private ArrayList<String> _parentPlugins;

	private String _plugin;

	public PluginEvent(String pluginName) {
		_parentPlugins = new ArrayList<>();
		for (Extension parent : PluginManager.lookup(this).getRegistry()
			.getPluginDescriptor(pluginName).getExtensions()) {
			_parentPlugins.add(parent.getExtendedPluginId()+"@"+
					parent.getExtendedPointId());
		}
		_plugin = pluginName;
	}

	public ArrayList<String> getParentPlugins() {
		return _parentPlugins;
	}

	public String getPlugin() {
		return _plugin;
	}
}
