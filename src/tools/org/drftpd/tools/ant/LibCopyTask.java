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
import org.java.plugin.registry.Library;
import org.java.plugin.registry.PluginAttribute;
import org.java.plugin.registry.PluginDescriptor;

import java.io.*;
import java.util.Collection;
import java.util.TreeSet;

/**
 * @author djb61
 * @version $Id$
 */
public class LibCopyTask extends Task {

	public static final boolean isWin32 = System.getProperty("os.name").startsWith("Windows");

	private boolean _slavePlugin;
	private String _distDir;
	private String _installDir;
	private FileSet _slaveFiles;

	/**
	 * @see org.apache.tools.ant.Task#execute()
	 */
	@Override
	@SuppressWarnings("unchecked")
	public void execute() throws BuildException {
		_distDir = getProject().getProperty("buildroot");
		_installDir = getProject().getProperty("installdir");
		// See if this is a slave plugin
		_slavePlugin = getProject().getProperty("slave.plugin").equalsIgnoreCase("true");
		_slaveFiles = getProject().getReference("slave.fileset");
		TreeSet<String> missingLibs = getProject().getReference("libs.missing");
		PluginDescriptor descriptor = getProject().getReference("plugin.descriptor");
		// First handle java libraries specified in the plugin manifest
		Collection<Library> jpfLibs = descriptor.getLibraries();
		for (Library lib : jpfLibs) {
			// not interested in the build files of the plugin itself
			if (lib.getPath().equalsIgnoreCase("classes/")) {
				continue;
			}
			File initial = new File(getProject().getProperty("basedir"),lib.getPath());
			try {
				File actual = initial.getCanonicalFile();
				if (actual.exists()) {
					try {
						File dest = copyFile(actual);
						logCopy(dest);
					} catch (IOException e2) {
						// error has already been logged
					}
				} else {
					String relativePath = actual.getPath().substring(_distDir.length()+1);
					missingLibs.add(relativePath);
				}
			} catch (IOException e) {
				log("Error resolving path for library from plugin manifest: "+lib.getPath(),Project.MSG_ERR);
			}
		}
		// Now handle any remaining libraries (eg native)
		PluginAttribute nativeDeps = descriptor.getAttribute("Native");
		if (nativeDeps != null) {
			for (PluginAttribute natDep : nativeDeps.getSubAttributes()) {
				String path = natDep.getValue();
				if (path != null) {
					File actual = new File(_distDir,path);
					if (actual.exists()) {
						try {
							File dest = copyFile(actual);
							logCopy(dest);
							PluginAttribute exec = natDep.getSubAttribute("Executable");
							if (exec != null) {
								String executable = exec.getValue();
								if (executable != null) {
									if (executable.equalsIgnoreCase("true") && !isWin32) {
										String[] cmdArray = {"chmod","755",dest.getAbsolutePath()};
										try {
											Process p = Runtime.getRuntime().exec(cmdArray);
											p.waitFor();
											if (p.exitValue() != 0) {
												log("Error chmodding file: "+dest.getAbsolutePath(),Project.MSG_ERR);
											}
										} catch (IOException e) {
											log("Error chmodding file: "+dest.getAbsolutePath(),Project.MSG_ERR);
										} catch (InterruptedException e) {
											log("Chmod process was interrupted on file: "+dest.getAbsolutePath(),Project.MSG_ERR);
										}
									}
								}
							}
						} catch (IOException e) {
							// error has already been logged
						}
					} else {
						String relativePath = actual.getPath().substring(_distDir.length()+1);
						missingLibs.add(relativePath);
					}
				}
			}
		}
	}

	private File copyFile(File currFile) throws IOException {
		String relativePath = currFile.getPath().substring(_distDir.length()+1);
		File targetFile = new File(_installDir,relativePath);
		if (targetFile.exists()) {
			log(targetFile.getPath()+" already exists, skipping libcopy",Project.MSG_INFO);
			return targetFile;
		}
		// make sure target dir exists
		if (!targetFile.getParentFile().exists()) {
			if (!targetFile.getParentFile().mkdirs()) {
				log("Unable to create target dir tree for "+targetFile.getPath(),Project.MSG_ERR);
				throw new IOException();
			}
		}
		FileInputStream fis = null;
		FileOutputStream fos = null;
		try {
			fis = new FileInputStream(currFile);
		} catch (FileNotFoundException e) {
			log("Library from plugin manifest appears to have been deleted: "+currFile.getPath(),Project.MSG_ERR);
			throw new IOException();
		}
		try {
			fos = new FileOutputStream(targetFile);
		} catch (FileNotFoundException e) {
			log("Unable to create target file to write to: "+targetFile.getPath(),Project.MSG_ERR);
			throw new IOException();
		}
		BufferedInputStream bis = new BufferedInputStream(fis);
		BufferedOutputStream bos = new BufferedOutputStream(fos);
		int read = 0;
		byte[] buff = new byte[65536];
		boolean success = true;
		while (read != -1 && success) {
			try {
				read = bis.read(buff,0,65536);
			} catch (IOException e) {
				log("Read error whilst reading from: "+currFile.getPath(),Project.MSG_ERR);
				success = false;
			}
			if (read != -1 && success) {
				try {
					bos.write(buff,0,read);
				} catch (IOException e) {
					log("Write error whilst writing to: "+targetFile.getPath(),Project.MSG_ERR);
					success = false;
				}
			}
		}
		try {
			bis.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			bos.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			fis.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			fos.close();
		} catch (IOException e) {
			// already closed
		}
		if (!success) {
			throw new IOException();
		}
		return targetFile;
	}

	private void logCopy(File copiedFile) {
		// Log copied file if needed
		if (_slavePlugin) {
			String relativeInstallPath = (copiedFile.getPath()).substring(_installDir.length()+1);
			_slaveFiles.appendIncludes(new String[]{relativeInstallPath});
		}
	}
}
