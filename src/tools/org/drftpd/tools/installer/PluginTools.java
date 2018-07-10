/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2.1 of the License, or
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
package org.drftpd.tools.installer;

import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginPrerequisite;
import org.java.plugin.registry.PluginRegistry;

import java.io.Serializable;
import java.util.*;

/**
 * @author djb61
 * @author Uses code from JPF by Dmitry Olshansky - http://jpf.sourceforge.net
 * @version $Id$
 */
public class PluginTools {

	public static ArrayList<PluginData> getPluginData(PluginRegistry registry) {
		Collection<PluginDescriptor> descriptors = registry.getPluginDescriptors();
		ArrayList<PluginData> plugins = new ArrayList<>();
		for (PluginDescriptor descr : descriptors) {
			plugins.add(new PluginData(descr));
		}
		plugins.sort(new PluginComparator());
		return plugins;
	}

	public static boolean isDepends(PluginData plugin1, PluginData plugin2, PluginRegistry registry) {
		// Circular (mutual) dependencies are treated as absence of dependency
		// at all.
		Set<PluginDescriptor> pre1 = new HashSet<>();
		Set<PluginDescriptor> pre2 = new HashSet<>();
		collectPrerequisites(plugin1.getDescriptor(), pre1, registry);
		collectPrerequisites(plugin2.getDescriptor(), pre2, registry);
		return pre1.contains(plugin2.getDescriptor()) && !pre2.contains(plugin1.getDescriptor());
	}

	public static boolean isSlaveDepends(PluginDescriptor plugin1, PluginDescriptor plugin2, PluginDescriptor master, PluginRegistry registry) {
		Set<PluginDescriptor> pre1 = new HashSet<>();
		collectPrerequisites(plugin1, pre1, registry);
		return pre1.contains(plugin2) && ! pre1.contains(master);
	}

	public static boolean isDependsInclImpl(PluginData plugin1, PluginData plugin2, PluginRegistry registry) {
		Set<PluginDescriptor> pre1 = new HashSet<>();
		collectImplPrerequisites(plugin1.getDescriptor(), pre1, registry);
		return pre1.contains(plugin2.getDescriptor());
	}

	private static void collectPrerequisites(PluginDescriptor descr, Set<PluginDescriptor> result, PluginRegistry registry) {
		for (PluginPrerequisite pre : descr.getPrerequisites()) {
			if (!pre.matches()) {
				continue;
			}
			PluginDescriptor descriptor = registry.getPluginDescriptor(pre.getPluginId());
			if (result.add(descriptor)) {
				collectPrerequisites(descriptor, result, registry);
			}
		}
	}

	private static void collectImplPrerequisites(PluginDescriptor descr, Set<PluginDescriptor> result, PluginRegistry registry) {
		for (PluginPrerequisite pre : descr.getPrerequisites()) {
			if (!pre.matches()) {
				continue;
			}
			PluginDescriptor descriptor = registry.getPluginDescriptor(pre.getPluginId());
			if (result.add(descriptor)) {
				collectImplPrerequisites(descriptor, result, registry);
			}
		}
		PluginAttribute deps = descr.getAttribute("ImplicitDependencies");
		if (deps != null) {
			for (PluginAttribute impDep : deps.getSubAttributes()) {
				PluginDescriptor descriptor = registry.getPluginDescriptor(impDep.getValue());
				if (descriptor != null) {
					if (result.add(descriptor)) {
						collectImplPrerequisites(descriptor, result, registry);
					}
				}
			}
		}
	}

	public static void reorder(List<PluginData> plugins, PluginRegistry registry) {
		for (int i = 0; i < plugins.size(); i++) {
			for (int j = i + 1; j < plugins.size(); j++) {
				if (isDepends(plugins.get(i), plugins.get(j), registry)) {
					Collections.swap(plugins, i, j);
					i = -1;
					break;
				}
			}
		}
	}

	public static ArrayList<String> getMissingPlugins(PluginRegistry pr, InstallerConfig config) {
		ArrayList<String> plugins = new ArrayList<>();
		for (String pId : config.getPluginSelections().keySet()) {
			boolean foundPlugin = false;
			for (PluginDescriptor pd : pr.getPluginDescriptors()) {
				if (pd.getId().equals(pId)) {
					//Configured plugin found
					foundPlugin = true;
					break;
				}
			}
			if (!foundPlugin) {
				//Configured plugin not installed
				plugins.add(pId);
			}
		}
		return plugins;
	}

	public static ArrayList<String> getUnconfiguredPlugins(PluginRegistry pr, InstallerConfig config) {
		ArrayList<String> plugins = new ArrayList<>();
		for (PluginDescriptor pd : pr.getPluginDescriptors()) {
			if (!config.getPluginSelections().containsKey(pd.getId())) {
				//Installed plugin not found in build configuration
				plugins.add(pd.getId());
			}
		}
		return plugins;
	}
}

@SuppressWarnings("serial")
class PluginComparator implements Comparator<PluginData>, Serializable {

	public int compare(PluginData plugin1, PluginData plugin2) {
		return plugin1.getName().compareTo(plugin2.getName());
	}
}
