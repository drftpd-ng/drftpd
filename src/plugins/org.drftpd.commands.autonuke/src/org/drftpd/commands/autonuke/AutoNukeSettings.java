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
package org.drftpd.commands.autonuke;

import org.drftpd.sections.SectionInterface;

import java.util.ArrayList;

/**
 * Holds all general AutoNuke settings
 * @author scitz0
 */
public class AutoNukeSettings {
    private static AutoNukeSettings ref;
    private ArrayList<SectionInterface> _excludedSections;
    private String _excludedDirs, _excludedSubDirs;
	private boolean _debug;
	private String _nukeUser;

	private AutoNukeSettings() {
        _excludedSections = new ArrayList<>();
		_excludedDirs = "";
		_excludedSubDirs = "";
		_debug = true;
		_nukeUser = "drftpd";
    }

	public void addExcludedSection(SectionInterface sec) {
		_excludedSections.add(sec);
	}
    public ArrayList<SectionInterface> getExcludedSections() {
		return _excludedSections;
	}
	public void clearExcludedSections() {
		_excludedSections.clear();
	}

	public void setExcludedDirs(String excludedDirs) {
		_excludedDirs = excludedDirs;
	}
	public String getExcludedDirs() {
		return _excludedDirs;
	}

	public void setExcludedSubDirs(String excludedSubDirs) {
		_excludedSubDirs = excludedSubDirs;
	}
	public String getExcludedSubDirs() {
		return _excludedSubDirs;
	}

	public void setDebug(boolean debug) {
		_debug = debug;
	}
	public boolean debug() {
		return _debug;
	}

	public void setNukeUser(String nukeUser) {
		_nukeUser = nukeUser;
	}
	public String getNukeUser() {
		return _nukeUser;
	}

    public static synchronized AutoNukeSettings getSettings() {
      if (ref == null)
          // it's ok, we can call this constructor
          ref = new AutoNukeSettings();
      return ref;
    }
}
