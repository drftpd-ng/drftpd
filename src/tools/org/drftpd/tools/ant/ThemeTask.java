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

import org.apache.tools.ant.BuildException;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.Task;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
public class ThemeTask extends Task {

	private File _baseDir;
	private File _pluginDir;
	private HashMap<String,String> _themes = new HashMap<>();
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
			FileOutputStream fos = null;
			OutputStreamWriter output = null;
			try {
				fos = new FileOutputStream(themeFile,true);
				output = new OutputStreamWriter(fos, StandardCharsets.ISO_8859_1);
				if (newFile) {
					// Since this is the first entry in the file during this build
					// session add the comment block at the top of the file
					ResourceBundle commentBundle = ResourceBundle.getBundle(this.getClass().getName());
					try {
						for (int i = 1;; i++) {
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
				log("Cannot write theme file to: " + themeFile.getParent(),Project.MSG_ERR);
			} catch (IOException e) {
				log("Error writing theme file: " + themeFile.getName(),Project.MSG_ERR);
			} finally {
				if (output != null) {
					try {
						output.close();
					} catch (IOException e) {
						// Just means it doesn't need closing
					}
				}
				if (fos != null) {
					try {
						fos.close();
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
			log("Skipping invalid filename: " + file.getName(),Project.MSG_INFO);
		} else {
			String keyPrefix = dirPrefix + "." + parts[0] + ".";
			FileInputStream fis = null;
			InputStreamReader input = null;
			try {
				// Create a BufferedReader to read the file
				fis = new FileInputStream(file);
				input = new InputStreamReader(fis, StandardCharsets.ISO_8859_1);
				PropertyResourceBundle inputBundle = new PropertyResourceBundle(input);

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
				TreeSet<String> sortedProps = new TreeSet<>(inputBundle.keySet());
				for (String propKey : sortedProps) {
					output.append(keyPrefix);
					output.append(propKey);
					output.append("=");
					String propValue = inputBundle.getString(propKey);
					if (propValue.indexOf('\n') != -1) {
						output.append("\\\n");
					}
					BufferedReader valueReader = new BufferedReader(
							new StringReader(propValue));
					try {
						String valueLine;
						int lineCount = 0;
						while ((valueLine = valueReader.readLine()) != null) {
							if (lineCount > 0) {
								output.append("\\n\\");
							}
							output.append(valueLine);
							lineCount++;
						}

					} catch(IOException e) {
						// As this is a string being read from this shouldn't happen
					}
					output.append("\n");
				}

				// Put modified theme back in the map
				_themes.put(parts[1], output.toString());
			} catch (FileNotFoundException e) {
				log("File appears to have been deleted, skipping: " + file.getName(),Project.MSG_ERR);
			} catch (IOException e) {
				log("Failed to load properties from: " + file.getName(),Project.MSG_ERR);
			} finally {
				try {
					input.close();
				} catch (IOException e) {
					// already closed
				}
				try {
					fis.close();
				} catch (IOException e) {
					// already closed
				}
			}
		}
	}
}
