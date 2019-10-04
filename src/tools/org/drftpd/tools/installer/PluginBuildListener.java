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

import org.apache.tools.ant.*;
import org.apache.tools.ant.taskdefs.Zip;
import org.apache.tools.ant.types.FileSet;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginRegistry;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.TreeSet;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginBuildListener implements SubBuildListener {

	private BufferedWriter _bw;
	private OutputStreamWriter _osw;
	private PipedInputStream _input;
	private PipedOutputStream _output;
	private PrintStream _sysErrHold;
	private PrintStream _sysOutHold;
	private int _logLevel;
	private long _startTime;
	private boolean _isSlavePlugin;
	private HashMap<String,PluginDescriptor> _pluginMap;
	private HashMap<String,PluginDescriptor> _slavePluginMap;
	private FileSet _slaveFiles;
	private ArrayList<String> _installedConfs;
	private TreeSet<String> _missingLibs;
	private InstallerConfig _config;
	private LogWindowInterface _logWindow;
	private int _pluginsDone;
	private boolean _cleanOnly;

	public PluginBuildListener(PipedInputStream input, InstallerConfig config, ArrayList<PluginData> buildPlugins, PluginRegistry registry, LogWindowInterface logWindow, boolean cleanOnly) {
		_input = input;
		_logLevel = config.getLogLevel();
		_config = config;
		_logWindow = logWindow;
		_pluginMap = new HashMap<>();
		_cleanOnly = cleanOnly;
		for (PluginData plugin : buildPlugins) {
			_pluginMap.put(plugin.getName(),plugin.getDescriptor());
		}
		ArrayList<PluginDescriptor> initialPlugins = new ArrayList<>();
		initialPlugins.add(_pluginMap.get("slave"));
		for (PluginData plugin : buildPlugins) {
			if (plugin.getName().equals("master")) {
				continue;
			}
			if (PluginTools.isSlaveDepends(plugin.getDescriptor(), _pluginMap.get("slave"), _pluginMap.get("master"), registry)) {
				initialPlugins.add(plugin.getDescriptor());
			}
		}
		_slavePluginMap = new HashMap<>();
		for (PluginDescriptor desc : initialPlugins) {
			_slavePluginMap.put(desc.getId(), desc);
			for (PluginData plugin : buildPlugins) {
				if (PluginTools.isSlaveDepends(desc, plugin.getDescriptor(), _pluginMap.get("master"), registry)) {
					_slavePluginMap.put(plugin.getName(),plugin.getDescriptor());
				}
			}
		}
		_slaveFiles = new FileSet();
		_installedConfs = new ArrayList<>();
		_missingLibs = new TreeSet<>();
		_pluginsDone = 0;
	}

	public void init() throws IOException {
		_isSlavePlugin = false;
		_output = new PipedOutputStream(_input);
		_osw = new OutputStreamWriter(_output);
		_bw = new BufferedWriter(_osw);
		_sysErrHold = System.err;
		_sysOutHold = System.out;
		System.setErr(new PrintStream(_output));
		System.setOut(new PrintStream(_output));
	}

	public void cleanup() {
		try {
			System.setErr(_sysErrHold);
			System.setOut(_sysOutHold);
			_bw.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			_osw.close();
		} catch (IOException e) {
			// already closed
		}
		try {
			_output.close();
		} catch (IOException e) {
			// already closed
		}
	}

	public void buildFinished(BuildEvent be) {
		// Create slave.zip
		Project pluginProject = be.getProject();
		if (pluginProject != null && be.getException() == null && !_config.getDevMode() && !_cleanOnly) {
			_logWindow.setProgressMessage("Building slave.zip");
			Zip slaveZip = new Zip();
			slaveZip.setProject(pluginProject);
			File slaveFile = new File(pluginProject.getProperty("installdir")+File.separator+"slave.zip");
			slaveZip.setDestFile(slaveFile);
			slaveZip.addFileset(_slaveFiles);
			try {
				writeLog("");
				writeLog("Building slave.zip");
				slaveZip.execute();
			} catch (BuildException e) {
				be.setException(e);
				e.printStackTrace();
			}
		}
		// Convert userfiles
		if (_config.getConvertUsers() && !_cleanOnly && be.getException() == null) {
			_logWindow.setProgressMessage("Converting userfiles");
			String userDir = _logWindow.getUserDir();
			UserFileConverter converter = new UserFileConverter(userDir,_config.getInstallDir());
			converter.convertUsers();
		}
		// Check for missing libs
		if (_missingLibs.size() > 0) {
			writeLog("");
			writeLog("The following files could not be found in the distribution directory:");
			for (String missLib : _missingLibs) {
				writeLog(missLib);
			}
			// Build failed, exit... Exit code used by CI
			System.exit(1);
		}
		writeLog("");
		if (be.getException() != null) {
			if (_cleanOnly) {
				writeLog("CLEAN FAILED");
			} else {
				writeLog("BUILD FAILED");
			}
			// Build failed, exit... Exit code used by CI
			System.exit(1);
		} else {
			if (_cleanOnly) {
				writeLog("CLEAN SUCCESSFUL");
			} else {
				writeLog("BUILD SUCCESSFUL");
			}
		}
		long endTime = System.currentTimeMillis();
		long seconds = (endTime - _startTime) / 1000;
		StringBuilder timeString = new StringBuilder("Total time: ");
		if (seconds >= 60) {
			long minutes = seconds / 60;
			seconds = seconds % 60;
			timeString.append(minutes);
			if (minutes > 1) {
				timeString.append(" minutes ");
			} else {
				timeString.append(" minute ");
			}
		}
		if (seconds > 0) {
			timeString.append(seconds);
			if (seconds > 1) {
				timeString.append(" seconds");
			} else {
				timeString.append(" second");
			}
		} else {
			timeString.append("under one second");
		}
		writeLog(timeString.toString());
		if (be.getException() != null && _config.getPrintTrace()) {
			writeLog("");
			be.getException().printStackTrace();
		}
		if (be.getException() == null) {
			if (_cleanOnly) {
				_logWindow.setProgressMessage("Clean complete");
			} else {
				_logWindow.setProgressMessage("Build complete");
			}
		} else {
			if (_cleanOnly) {
				_logWindow.setProgressMessage("Clean failed");
			} else {
				_logWindow.setProgressMessage("Build failed");
				// Build failed, exit... Exit code used by CI
				System.exit(1);
			}
		}
	}

	public void buildStarted(BuildEvent be) {
		File installDir = new File(be.getProject().getProperty("installdir"));
		_slaveFiles.setDir(installDir);
		_startTime = System.currentTimeMillis();
		writeLog(be.getMessage());
		writeLog("");
	}

	public void messageLogged(BuildEvent be) {
		if (be.getPriority() <= _logLevel) {
			String prefix = "";
			Task task = be.getTask();
			if (task != null) {
				String taskName = task.getTaskName();
				if (taskName != null) {
					prefix = padToLength(taskName) + " ";
				}
			}
			writeLog(prefix+be.getMessage());
		}
	}

	public void subBuildFinished(BuildEvent be) {
		// reset state
		_isSlavePlugin = false;
		_pluginsDone++;
		_logWindow.setProgress(_pluginsDone);
	}

	public void subBuildStarted(BuildEvent be) {
		Project p = be.getProject();
		if (p != null) {
			String name = p.getName();
			if (name != null) {
				PluginDescriptor currDescriptor = _pluginMap.get(name);
				_isSlavePlugin = _slavePluginMap.containsKey(name);
				p.setProperty("slave.plugin",String.valueOf(_isSlavePlugin));
				p.addReference("plugin.descriptor",currDescriptor);
				p.addReference("slave.fileset",_slaveFiles);
				p.addReference("installed.confs",_installedConfs);
				p.addReference("libs.missing",_missingLibs);
			}
		}
	}

	public void targetFinished(BuildEvent be) {

	}

	public void targetStarted(BuildEvent be) {

	}

	public void taskFinished(BuildEvent be) {

	}

	public void taskStarted(BuildEvent be) {

	}

	private void writeLog(String message) {
		try {
			_bw.write(message);
			_bw.write("\n");
			_bw.flush();
		} catch (IOException e) {
			// ignore for the moment
		}
	}

	private String padToLength(String str) {
		StringBuilder sb = new StringBuilder();
		int len = str.length();
		for (int i = len+2;i < 14;i++) {
			sb.append(" ");
		}
		sb.append("[");
		sb.append(str);
		sb.append("]");
		return sb.toString();
	}
}
