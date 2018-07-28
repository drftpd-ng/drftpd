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
package org.drftpd.tools.installer.auto;

import org.drftpd.tools.installer.*;

import java.io.*;

public class LogWindow implements LogWindowInterface {

	private boolean _fileLogEnabled;
	private boolean _suppressLog;
	private FileLogger _fileLog;
	private BufferedReader _logReader;
	private PipedInputStream _logInput;
	private PluginBuilder _builder;
	private int _pluginCount;
	private boolean _cleanOnly;
	private PrintStream _defaultOut;

	public LogWindow(PipedInputStream logInput, InstallerConfig config, int pluginCount, boolean cleanOnly) {
		_fileLogEnabled = config.getFileLogging();
		_suppressLog = config.getSuppressLog();
		_logInput = logInput;
		_pluginCount = pluginCount;
		_cleanOnly = cleanOnly;
		//Create a reference to original System.out as it will be reassigned by PluginBuildListener
		_defaultOut = new PrintStream(new FileOutputStream(FileDescriptor.out));
	}

	public void setBuilder(PluginBuilder builder) {
		_builder = builder;
	}

	public void init() throws IOException {
		if (_fileLogEnabled) {
			_fileLog = new FileLogger();
			_fileLog.init();
		}
		_logReader = new BufferedReader(new InputStreamReader(_logInput));
		new Thread(new ReadingThread()).start();
		new Thread(new PluginBuilderThread(_builder)).start();
	}

	/**
	 * Used to get dr2 dir for converting user files.
	 * Not a valid option for the autobuilder and will never be called.
	 * @return null
	 */
	public String getUserDir() {
		return null;
	}

	public void setProgress(int pluginsDone) {
		if (_cleanOnly) {
			_defaultOut.println("Cleaned " + pluginsDone + "/" + _pluginCount + " plugins");
		} else {
			_defaultOut.println("Built " + pluginsDone + "/" + _pluginCount + " plugins");
		}
	}

	public void setProgressMessage(String message) {
		System.out.println(message);
	}

	private class ReadingThread implements Runnable {

		public void run() {
			try {
				String logLine;
				do {
					logLine = _logReader.readLine();
					if (logLine != null) {
						if (_fileLogEnabled) {
							_fileLog.writeLog(logLine);
						}
						if (!_suppressLog) {
							_defaultOut.println(logLine);
						}
					}
				} while(logLine != null);
			} catch (Exception e) {
				// Ignore
			} finally {
				// cleanup
				if (_fileLogEnabled) {
					_fileLog.cleanup();
				}
				try {
					_logReader.close();
				} catch (IOException e) {
					// already closed
				}
				try {
					_logInput.close();
				} catch (IOException e) {
					// already closed
				}
			}
		}
	}
}
