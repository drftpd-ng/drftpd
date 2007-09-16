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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.InputStreamReader;
import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Task;

/**
 * @author djb61
 * @version $Id$
 */
public class ThemeTask extends Task {

	private File _baseDir;
	private File _pluginDir;
	private HashMap<String,String> _themes = new HashMap<String,String>();
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
		// Get the build start time as long
		SimpleDateFormat simpleBuildDate = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
		Date buildDate = null;
		try {
			buildDate = simpleBuildDate.parse(getProject().getProperty("build.plugins.start"));
		} catch (ParseException e) {
			throw new BuildException("Plugin build timestamp not set correctly");
		}
		long longDate = buildDate.getTime();
		findProperties(_pluginDir);
		// Check all theme files we touched and write them to disk
		for (String theme : _themes.keySet()) {
			File themeFile = new File(
					_baseDir+File.separator+themedir+File.separator+theme+File.separator+"core.theme.default");
			// Check we have a dir for this theme, if not make it
			if (!themeFile.getParentFile().exists()) {
				themeFile.getParentFile().mkdirs();
			}
			boolean newFile = false;
			// Delete stale file if needed
			if (themeFile.lastModified() == 0L || themeFile.lastModified() < longDate) {
				// Safe to try a delete even if the file doesn't exist
				themeFile.delete();
				newFile = true;
			}
			FileWriter output = null;
			try {
				output = new FileWriter(themeFile,true);
				if (newFile) {
					// Since this is the first entry in the file during this build
					// session add the comment block at the top of the file
					ResourceBundle commentBundle = ResourceBundle.getBundle(this.getClass().getName());
					try {
						for (int i = 1;; i++) {
							log(commentBundle.getString("comment."+i));
							output.write(commentBundle.getString("comment."+i)+"\n");
						}
					} catch (MissingResourceException e) {
						// Means we reached the end of the comment block
						output.write("\n");
					}
				}
				// Append new theme data to file
				output.write(_themes.get(theme));
				output.flush();
			} catch (FileNotFoundException e) {
				log("Cannot write theme file to: " + themeFile.getParent());
			} catch (IOException e) {
				log("Error writing theme file: " + themeFile.getName());
			} finally {
				if (output != null) {
					try {
						output.close();
					} catch (IOException e) {
						// Just means it doesn't need closing
					}
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
			BufferedReader input = null;
			try {
				// Create a BufferedReader to read the file
				fis = new FileInputStream(file);
				input = new BufferedReader(new InputStreamReader(fis));

				// Retrieve string object for the theme this
				// this file belongs to, if we don't have one
				// yet then create one
				String existing = _themes.get(parts[1]);
				StringBuilder output = null;
				if (existing == null) {
					output = new StringBuilder();
				} else {
					output = new StringBuilder(existing);
				}

				// Add a new line to ease readability
				output.append("\n");

				// Copy all properties from file into theme
				// adding the correct namespace prefix
				while (input.ready()) {
					String line = input.readLine();
					if (line.indexOf('=') != -1) {
						output.append(keyPrefix);
					}
					output.append(line);
					output.append("\n");
				}

				// Put modified theme back in the map
				_themes.put(parts[1], output.toString());
			} catch (FileNotFoundException e) {
				log("File appears to have been deleted, skipping: " + file.getName());
			} catch (IOException e) {
				log("Failed to load properties from: " + file.getName());
			} finally {
				try {
					input.close();
					fis.close();
				} catch (IOException e) {
					// FileInputStream is already closed
				}
			}
		}
	}
}
