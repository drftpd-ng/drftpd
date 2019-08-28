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
package org.drftpd.tools.installer.swing;

import org.drftpd.tools.installer.FileLogger;
import org.drftpd.tools.installer.InstallerConfig;
import org.drftpd.tools.installer.LogWindowInterface;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.GridBagLayout;
import java.awt.GridBagConstraints;

import java.io.*;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class LogWindow extends JFrame implements LogWindowInterface {

	private boolean _fileLogEnabled;
	private boolean _suppressLog;
	private FileLogger _fileLog;
	private JButton _buildButton;
	private JButton _cleanButton;
	private JButton _exitButton;
	private JButton _okButton;
	private JButton _selectAllButton;
	private JProgressBar _progressBar;
	private JTextArea _logArea;
	private BufferedReader _logReader;
	private PipedInputStream _logInput;
	private int _pluginCount;
	private boolean _cleanOnly;

	public LogWindow(PipedInputStream logInput, JButton buildButton, JButton cleanButton, JButton selectAllButton, JButton exitButton, InstallerConfig config, int pluginCount, boolean cleanOnly) {
		super("Build Log");
		_fileLogEnabled = config.getFileLogging();
		_suppressLog = config.getSuppressLog();
		_logInput = logInput;
		_buildButton = buildButton;
		_cleanButton = cleanButton;
		_selectAllButton = selectAllButton;
		_exitButton = exitButton;
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
		logPane.setBorder(new TitledBorder(new EtchedBorder(),"Plugin Build Log"));
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
		southPanel.add(_progressBar, new GridBagConstraints(0,0,1,1,100.0,0.0
				,GridBagConstraints.NORTH, GridBagConstraints.BOTH, new Insets(5, 5, 5, 5), 0, 0));
		_okButton = new JButton();
		_okButton.setText("OK");
		_okButton.setEnabled(false);
		_okButton.setPreferredSize(new Dimension(100,25));
		_okButton.addActionListener(e -> {
            setVisible(false);
            dispose();
        });
		southPanel.add(_okButton, new GridBagConstraints(0,1,1,1,0.0,0.0
				,GridBagConstraints.SOUTH, GridBagConstraints.NONE, new Insets(5, 5, 5, 0), 0, 0));
		contentPane.add(southPanel, BorderLayout.SOUTH);
		setSize(500, 400);
		validate();
		setVisible(true);
	}

	public void init() throws IOException {
		if (_fileLogEnabled) {
			_fileLog = new FileLogger();
			_fileLog.init();
		}
		_logReader = new BufferedReader(new InputStreamReader(_logInput));
		// disable buttons whilst build is in progress
		_buildButton.setEnabled(false);
		_cleanButton.setEnabled(false);
		_selectAllButton.setEnabled(false);
		_exitButton.setEnabled(false);
		new Thread(new ReadingThread()).start();
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
							_logArea.setCaretPosition(_logArea.getText().length());
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
				// build thread has finished, re-enable buttons
				_okButton.setEnabled(true);
				_buildButton.setEnabled(true);
				_cleanButton.setEnabled(true);
				_selectAllButton.setEnabled(true);
				_exitButton.setEnabled(true);
				// change default focus to "ok" button for usability
				_okButton.requestFocusInWindow();
			}
		}
	}
}
