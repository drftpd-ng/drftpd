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

import java.beans.XMLEncoder;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;

/**
 * @author djb61
 * @version $Id$
 */
public class InstallerConfig implements Serializable {

	private String _installDir;
	private String _logLevel;
	private boolean _consoleLogging;
	private HashMap<String,Boolean> _pluginSelections;
	
	public InstallerConfig() {
		
	}

	public void setInstallDir(String installDir) {
		_installDir = installDir;
	}

	public void setLogLevel(String logLevel) {
		_logLevel = logLevel;
	}

	public void setConsoleLogging(boolean consoleLogging) {
		_consoleLogging = consoleLogging;
	}

	public void setPluginSelections(HashMap<String,Boolean> pluginSelections) {
		_pluginSelections = pluginSelections;
	}

	public String getInstallDir() {
		return _installDir;
	}

	public String getLogLevel() {
		return _logLevel;
	}

	public boolean getConsoleLogging() {
		return _consoleLogging;
	}

	public HashMap<String,Boolean> getPluginSelections() {
		return _pluginSelections;
	}

	public void writeToDisk() throws IOException {
		XMLEncoder out = null;
		try {
			out = new XMLEncoder(new FileOutputStream("build.conf"));
			out.writeObject(this);
		} finally {
			if (out != null)
				out.close();
		}
	}
}
