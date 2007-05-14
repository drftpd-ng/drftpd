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
package org.drftpd.tools.ant;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Properties;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * @author djb61
 * @version $Id$
 */
public class ThemeTask extends Task {

	private File _baseDir;
	private File _pluginDir;
	private HashMap<String,Properties> _themes = new HashMap<String,Properties>();
	private final String themedir = "conf" + File.separator + "themes";

	/**
	 * @param aBaseDir base directory for project
	 */
	public final void setBaseDir(final File aBaseDir) {
		_baseDir = aBaseDir;
	}

	/**
	 * @param aPluginDir base directory for plugin
	 */
	public final void setPluginDir(final File aPluginDir) {
		_pluginDir = aPluginDir;
	}

	/**
	 * @see org.apache.tools.ant.Task#execute()
	 */
	@Override
	public void execute() throws BuildException {
		findProperties(_pluginDir);
		// Check all theme files we touched and write them to disk
		for (String theme : _themes.keySet()) {
			File themeFile = new File(
					_baseDir+File.separator+themedir+File.separator+theme+File.separator+"core.theme.default");
			// Check we have a dir for this theme, if not make it
			if (!themeFile.getParentFile().exists()) {
				File subThemeDir = themeFile.getParentFile();
				subThemeDir.mkdirs();
			}
			FileInputStream fis = null;
			Properties existingTheme = new Properties();
			try {
				fis = new FileInputStream(themeFile);
				existingTheme.load(fis);
			} catch (IOException e) {
				// Not a problem, just means no existing data for this theme
			} finally {
				try {
					if (fis != null) {
						fis.close();
					}
				} catch (IOException e) {
					// FileInputStream already closed
				}
			}
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(themeFile);
				Properties themeProps = _themes.get(theme);
				// Merge in to existing theme data if there is any
				for (Object o : themeProps.keySet()) {
					String key = (String) o;
					existingTheme.setProperty(key, themeProps.getProperty(key));
				}
				existingTheme.store(fos,null);
			} catch (FileNotFoundException e) {
				log("Cannot write theme file to: "+themeFile.getParent());
			} catch (IOException e) {
				log("Error writing theme file: " + themeFile.getName());
			} finally {
				try {
					fos.close();
				} catch (IOException e) {
					// FileOutputStream already closed
				}
			}
		}
	}

	/**
	 * Recursively scans a directory for properties files
	 * and adds their entries to their respective theme
	 * file.
	 * 
	 * @param dir directory to search for properties
	 * @throws BuildException
	 */
	private void findProperties(File dir) throws BuildException {
		if (!dir.isDirectory())
			throw new BuildException(dir.getPath() + " is not a directory");

		for (File file : dir.listFiles()) {
			if (file.getName().startsWith(".")) {
				continue;
			} else if (file.isFile() && file.getName().endsWith(".properties")) {
				loadProperties(file);
			} else if (file.isDirectory()){
				findProperties(file);
			}
		}
	}

	/**
	 * Read a properties file and add the keys to the respective
	 * theme properties
	 * 
	 * @param file properties file to be read
	 */
	private void loadProperties(File file) {
		String dirPrefix = file.getParent().substring(_pluginDir.getPath().length()+1);
		dirPrefix = dirPrefix.replace(File.separatorChar,'.');
		String[] parts = file.getName().split("\\.");
		if (parts.length != 3) {
			log("Skipping invalid filename: " + file.getName());
		} else {
			String keyPrefix = dirPrefix + "." + parts[0] + ".";
			FileInputStream fis = null;
			try {
				// Read current file into a properties object
				fis = new FileInputStream(file);
				Properties input = new Properties();
				input.load(fis);

				// Retrieve properties object for the theme this
				// this file belongs to, if we don't have one
				// yet then create one
				Properties output = _themes.get(parts[1]);
				if (output == null) {
					output = new Properties();
					_themes.put(parts[1], output);
				}

				// Copy all properties from file into theme
				// adding the correct namespace prefix
				for (Object o : input.keySet()) {
					String key = (String) o;
					String value = input.getProperty(key);
					output.setProperty(keyPrefix + key, value);
				}
			} catch (FileNotFoundException e) {
				log("File appears to have been deleted, skipping: " + file.getName());
			} catch (IOException e) {
				log("Failed to load properties from: " + file.getName());
			} finally {
				try {
					fis.close();
				} catch (IOException e) {
					// FileInputStream is already closed
				}
			}
		}
	}
}
