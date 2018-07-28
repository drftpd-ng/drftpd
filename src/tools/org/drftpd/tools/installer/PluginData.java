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
package org.drftpd.tools.installer;

import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginData {

	private String _name;
	private PluginDescriptor _descr;
	private boolean _selected;
	private boolean _modifiable;

	public PluginData(PluginDescriptor descr) {
		_name = descr.getId();
		_descr = descr;
		PluginAttribute coreBuild = descr.getAttribute("DefaultBuild");
		PluginAttribute mustBuild = descr.getAttribute("MustBuild");
        _selected = coreBuild != null && coreBuild.getValue().equalsIgnoreCase("true");
        _modifiable = mustBuild == null || !mustBuild.getValue().equalsIgnoreCase("true");
	}

	public String getName() {
		return _name;
	}

	public PluginDescriptor getDescriptor() {
		return _descr;
	}

	public void setSelected(Object value) { 
		_selected = (Boolean) value;
	}

	public void invertSelected() {
		_selected = !_selected;
	}

	public boolean isSelected() {
		return _selected;
	}

	public boolean isModifiable() {
		return _modifiable;
	}

	public String toString() {
		return _name;
	}
}
