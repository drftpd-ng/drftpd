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

import java.beans.XMLDecoder;
import java.io.FileInputStream;
import java.util.HashMap;

/**
 * @author djb61
 * @version $Id$
 */
public class ConfigReader {

	public ConfigReader() {
		
	}

	public InstallerConfig getConfig() {
		XMLDecoder xd = null;
		InstallerConfig config = null;
		try {
			xd = new XMLDecoder(new FileInputStream("build.conf"));
			config = (InstallerConfig) xd.readObject();
			return config;
		} catch (Exception e) {
			// Error loading config, let's use some defaults
			config = new InstallerConfig();
			config.setInstallDir(System.getProperty("user.dir"));
			config.setLogLevel(1);
			config.setFileLogging(false);
			config.setClean(false);
			config.setConvertUsers(false);
			config.setPrintTrace(true);
			config.setSuppressLog(false);
			config.setDevMode(false);
			config.setPluginSelections(new HashMap<>());
			return config;
		} finally {
			if (xd != null)
				xd.close();
		}
	}
}
