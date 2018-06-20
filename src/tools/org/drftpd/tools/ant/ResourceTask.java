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
import org.apache.tools.ant.types.FileSet;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

/**
 * @author djb61
 * @version $Id$
 */
public class ResourceTask extends Task {

	public static final boolean isWin32 = System.getProperty("os.name").startsWith("Windows");

	private File _baseDir;
	private File _resourceDir;
	private long _longDate = 0L;
	private boolean _slavePlugin;
	private ArrayList<String> _filePatterns;
	private ArrayList<String> _installedConfs;

	/**
	 * @param aBaseDir base directory for project
	 */
	public final void setBaseDir(final File aBaseDir) {
		_baseDir = aBaseDir;
	}

	/**
	 * @param aResourceDir base directory for resources
	 */
	public final void setResourceDir(final File aResourceDir) {
		_resourceDir = aResourceDir;
	}

	/**
	 * @see org.apache.tools.ant.Task#execute()
	 */
	@SuppressWarnings("unchecked")
	@Override
	public void execute() throws BuildException {
		// See if this is a slave plugin
		_slavePlugin = getProject().getProperty("slave.plugin").equalsIgnoreCase("true");
		FileSet slaveFiles = getProject().getReference("slave.fileset");
		_filePatterns = new ArrayList<>();
		// Get the build start time as long
		SimpleDateFormat simpleBuildDate = new SimpleDateFormat("yyyy.MM.dd HH:mm:ss.SSS");
		Date buildDate = null;
		try {
			buildDate = simpleBuildDate.parse(getProject().getProperty("build.plugins.start"));
		} catch (ParseException e) {
			throw new BuildException("Plugin build timestamp not set correctly");
		}
		_longDate = buildDate.getTime();
		_installedConfs = getProject().getReference("installed.confs");
		findResources(_resourceDir);
		if (_slavePlugin && !_filePatterns.isEmpty()) {
			String[] patterns = _filePatterns.toArray(new String[_filePatterns.size()]);
			slaveFiles.appendIncludes(patterns);
		}
	}

	/**
	 * Recursively scans a directory for resource files
	 * and adds their entries to the installed resource
	 * files.
	 * 
	 * @param dir directory to search for resources
	 * @throws BuildException
	 */
	private void findResources(File dir) throws BuildException {
		if (!dir.isDirectory()) {
			log("Resource directory "+dir.getPath()+" not found",Project.MSG_INFO);
			return;
		}

		for (File file : dir.listFiles()) {
			if (file.getName().startsWith(".")) {
            } else if (file.isFile()) {
				copyResource(file);
			} else if (file.isDirectory()){
				findResources(file);
			}
		}
	}

	/**
	 * Copies a resource file into the installed directory
	 * hierarchy, if the file already exists and was created
	 * during this build session the contents are appended
	 * to the existing file, if the file is from an earlier
	 * build session it will be deleted and replaced
	 * 
	 * @param file resource file to be copied
	 */
	private void copyResource(File file) {
		String relativePath = file.getPath().substring(_resourceDir.getPath().length()+1);
		File newFile = new File(_baseDir, relativePath);
		// Check we have a dir for this resource, if not make it
		if (!newFile.getParentFile().exists()) {
			newFile.getParentFile().mkdirs();
		}
		// Delete stale file if needed
		if (newFile.lastModified() == 0L || newFile.lastModified() < _longDate) {
			// Safe to try a delete even if the file doesn't exist
			newFile.delete();
		}
		FileInputStream fis = null;
		BufferedReader input = null;
		StringBuilder output = new StringBuilder();
		try {
			// Create a BufferedReader to read the file
			fis = new FileInputStream(file);
			input = new BufferedReader(new InputStreamReader(fis));

			// Read all lines from file
			while (input.ready()) {
				output.append(input.readLine());
				output.append("\n");
			}
		} catch (FileNotFoundException e) {
			log("Resource file appears to have been deleted, skipping: " + file.getName(),Project.MSG_ERR);
		} catch (IOException e) {
			log("Failed to load resources from: " + file.getName(),Project.MSG_ERR);
		} finally {
			try {
				input.close();
			} catch (IOException e) {
				// BufferedReader is already closed
			}
			try {
				fis.close();
			} catch (IOException e) {
				// FileInputStream is already closed
			}
		}
		// Write data to new file
		FileWriter outputWriter = null;
		try {
			outputWriter = new FileWriter(newFile,true);
			outputWriter.write(output.toString()+"\n");
			outputWriter.flush();
			if (_slavePlugin) {
				_filePatterns.add(relativePath);
			}
		} catch (IOException e) {
			log("Error writing resource file: " + newFile.getName(),Project.MSG_ERR);
		} finally {
			try {
				outputWriter.close();
			} catch (IOException e) {
				// FileWriter is already closed
			}
		}
		// See if this is a dist file that needs installing
		if (relativePath.endsWith(".dist")) {
			String installRelativePath = relativePath.substring(0, relativePath.lastIndexOf(".dist"));
			File installConfFile = new File(_baseDir, installRelativePath);
			boolean doInstall = true;
			if (installConfFile.exists()) {
				if (!_installedConfs.contains(installRelativePath)) {
					doInstall = false;
				}
			} else {
				_installedConfs.add(installRelativePath);
			}
			if (doInstall) {
				// Write data to installed file
				FileWriter installOutputWriter = null;
				try {
					installOutputWriter = new FileWriter(installConfFile,true);
					installOutputWriter.write(output.toString()+"\n");
					installOutputWriter.flush();
					if (_slavePlugin) {
						_filePatterns.add(installRelativePath);
					}
				} catch (IOException e) {
					log("Error installing resource file: " + installConfFile.getName(),Project.MSG_ERR);
				} finally {
					try {
						installOutputWriter.close();
					} catch (IOException e) {
						// FileWriter is already closed
					}
				}
			}
		}
		// If non windows and a shell script then chmod
		if (!isWin32) {
			if (newFile.getName().endsWith(".sh")) {
				String[] cmdArray = {"chmod","755",newFile.getAbsolutePath()};
				try {
					Process p = Runtime.getRuntime().exec(cmdArray);
					p.waitFor();
					if (p.exitValue() != 0) {
						log("Error chmodding file: "+newFile.getAbsolutePath(),Project.MSG_ERR);
					}
				} catch (IOException e) {
					log("Error chmodding file: "+newFile.getAbsolutePath(),Project.MSG_ERR);
				} catch (InterruptedException e) {
					log("Chmod process was interrupted on file: "+newFile.getAbsolutePath(),Project.MSG_ERR);
				}
			}
		}
	}
}
