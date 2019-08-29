package org.drftpd.tools.installer.console;
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

import charva.awt.BorderLayout;
import charva.awt.Container;
import charva.awt.Color;
import charva.awt.Dimension;
import charva.awt.Insets;
import charva.awt.GridBagLayout;
import charva.awt.GridBagConstraints;
import charva.awt.Toolkit;
import charvax.swing.*;
import charvax.swing.border.LineBorder;
import charvax.swing.border.TitledBorder;
import org.drftpd.tools.installer.*;

import java.io.*;

/**
 * @author djb61
 * @version $Id$
 */
public class LogWindow extends JFrame implements LogWindowInterface {

	private static final Toolkit toolkit = Toolkit.getDefaultToolkit();

	private boolean _fileLogEnabled;
	private boolean _suppressLog;
	private FileLogger _fileLog;
	private JButton _okButton;
	private JProgressBar _progressBar;
	private JTextArea _logArea;
	private BufferedReader _logReader;
	private PipedInputStream _logInput;
	private PluginBuilder _builder;
	private int _pluginCount;
	private boolean _cleanOnly;

	public LogWindow(PipedInputStream logInput, InstallerConfig config, int pluginCount, boolean cleanOnly) {
		super("Build Log");
		_fileLogEnabled = config.getFileLogging();
		_suppressLog = config.getSuppressLog();
		_logInput = logInput;
		_pluginCount = pluginCount;
		_cleanOnly = cleanOnly;
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());
		JPanel centerPanel = new JPanel();
		BorderLayout centerLayout = new BorderLayout();
		centerPanel.setLayout(centerLayout);
		_logArea = new JTextArea();
		_logArea.setLineWrap(true);
		_logArea.setEditable(false);
		if (_suppressLog) {
			_logArea.setText("LOGGING SUPPRESSED");
		} else {
			_logArea.setText("");
		}
		JScrollPane logPane = new JScrollPane(_logArea);
		TitledBorder pluginBorder = new TitledBorder(new LineBorder(Color.white));
		pluginBorder.setTitle("Plugin Build Log");
		logPane.setViewportBorder(pluginBorder);
		_logArea.setColumns(toolkit.getScreenColumns() - 4);
		_logArea.setRows(toolkit.getScreenRows() - 12);
		centerPanel.add(logPane, BorderLayout.CENTER);
		contentPane.add(centerPanel, BorderLayout.CENTER);
		JPanel southPanel = new JPanel();
		GridBagLayout southLayout = new GridBagLayout();
		southPanel.setLayout(southLayout);
		_progressBar = new JProgressBar(0, _pluginCount);
		_progressBar.setValue(0);
		if (_cleanOnly) {
			_progressBar.setString("Cleaned 0/"+_pluginCount+" plugins");
		} else {
			_progressBar.setString("Built 0/"+_pluginCount+" plugins");
		}
		_progressBar.setStringPainted(true);
		_progressBar.setSize(new Dimension(toolkit.getScreenColumns() - 4, 1));
		southPanel.add(_progressBar, new GridBagConstraints(0,0,1,1,100.0,0.0
				,GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(0, 1, 0, 1), 0, 0));
		_okButton = new JButton();
		_okButton.setText("OK");
		_okButton.setEnabled(false);
		_okButton.addActionListener(e -> setVisible(false));
		southPanel.add(_okButton, new GridBagConstraints(0,1,1,1,100.0,0.0
				,GridBagConstraints.SOUTH, GridBagConstraints.NONE, new Insets(0, 1, 0, 1), 0, 0));
		contentPane.add(southPanel, BorderLayout.SOUTH);
		setSize(toolkit.getScreenColumns(),toolkit.getScreenRows() - 6);
		setLocation(0,2);
		validate();
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
		setVisible(true);
	}

	public String getUserDir() {
		JFileChooser userFileChooser = new JFileChooser(new File(System.getProperty("user.dir")));
		userFileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
		userFileChooser.setDialogTitle("Select directory DrFTPd 2.0 is installed in");
		int result = userFileChooser.showOpenDialog(this);
		if (result == JFileChooser.APPROVE_OPTION) {
			return userFileChooser.getSelectedFile().getPath();
		}
		
		return null;
	}

	public void setProgress(int pluginsDone) {
		_progressBar.setValue(pluginsDone);
		if (_cleanOnly) {
			_progressBar.setString("Cleaned "+pluginsDone+"/"+_pluginCount+" plugins");
		} else {
			_progressBar.setString("Built "+pluginsDone+"/"+_pluginCount+" plugins");
		}
	}

	public void setProgressMessage(String message) {
		_progressBar.setString(message);
	}

	private class ReadingThread implements Runnable {

		public void run() {
			try {
				String logLine = null;
				do {
					logLine = _logReader.readLine();
					if (logLine != null) {
						if (_fileLogEnabled) {
							_fileLog.writeLog(logLine);
						}
						if (!_suppressLog) {
							_logArea.append(logLine+"\n");
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
				// build thread has finished, enable button
				_okButton.setEnabled(true);
				// change default focus to "ok" button for usability
				_okButton.requestFocus();
			}
		}
	}
}

