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
package org.drftpd.tools.installer.console;

import charva.awt.*;
import charva.awt.event.ActionEvent;
import charva.awt.event.ActionListener;
import charva.awt.event.ItemEvent;
import charva.awt.event.ItemListener;
import charvax.swing.*;
import org.drftpd.tools.installer.InstallerConfig;

import java.io.File;
import java.util.ArrayList;

/**
 * @author djb61
 * @version $Id$
 */
public class ConfigPanel extends JPanel implements ActionListener, ItemListener {

	private static final Toolkit toolkit = Toolkit.getDefaultToolkit();

	private ArrayList<String> _logList;
	private JButton _dirBrowse;
	private JComboBox _logLevel;
	private JCheckBox _fileLog;
	private JCheckBox _clean;
	private JCheckBox _convertUsers;
	private JCheckBox _suppressLog;
	private JCheckBox _printTrace;
	private JCheckBox _devMode;
	private JLabel _logNotice;
	private JTextField _installLocation;
	private InstallerConfig _config;

	public ConfigPanel(InstallerConfig config) {
		_config = config;
		_logList = new ArrayList<>();
		setLayout(new BorderLayout());
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new GridBagLayout());
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new FlowLayout());

		JLabel installLabel = new JLabel();
		installLabel.setText("Install location: ");
		_installLocation = new JTextField();
		_installLocation.setColumns(toolkit.getScreenColumns() - 34);
		_installLocation.setText(_config.getInstallDir());

		_dirBrowse = new JButton();
		_dirBrowse.setText("..");
		_dirBrowse.addActionListener(this);
		
		JLabel logLevelLabel = new JLabel();
		logLevelLabel.setText("Build log level: ");
		_logLevel = new JComboBox();
		String logName;
		logName = "ERROR";
		_logLevel.addItem(logName);
		_logList.add(logName);
		logName = "WARN";
		_logLevel.addItem(logName);
		_logList.add(logName);
		logName = "INFO";
		_logLevel.addItem(logName);
		_logList.add(logName);
		logName = "VERBOSE";
		_logLevel.addItem(logName);
		_logList.add(logName);
		logName = "DEBUG";
		_logLevel.addItem(logName);
		_logList.add(logName);
		_logLevel.setMaximumRowCount(5);
		_logLevel.addItemListener(this);
		_logLevel.setSelectedIndex(_config.getLogLevel());

		JLabel fileLogLabel = new JLabel();
		fileLogLabel.setText("Enable file logging: ");
		_fileLog = new JCheckBox();
		_fileLog.setSelected(_config.getFileLogging());
		_fileLog.addActionListener(e -> updateLogLabel());
		JLabel cleanLabel = new JLabel();
		cleanLabel.setText("Clean before build: ");
		_clean = new JCheckBox();
		_clean.setSelected(_config.getClean());
		JLabel convertLabel = new JLabel();
		convertLabel.setText("Convert 2.0 user files: ");
		_convertUsers = new JCheckBox();
		_convertUsers.setSelected(_config.getConvertUsers());
		JLabel suppressLabel = new JLabel();
		suppressLabel.setText("Suppress UI logging: ");
		_suppressLog = new JCheckBox();
		_suppressLog.setSelected(_config.getSuppressLog());
		JLabel printTraceLabel = new JLabel();
		printTraceLabel.setText("Print Stack Trace: ");
		_printTrace = new JCheckBox();
		_printTrace.setSelected(_config.getPrintTrace());
		JLabel devModeLabel = new JLabel();
		devModeLabel.setText("Developer Mode: ");
		_devMode = new JCheckBox();
		_devMode.setSelected(_config.getDevMode());

		_logNotice = new JLabel();
		updateLogLabel();

		centerPanel.add(installLabel, new GridBagConstraints(0,0,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_installLocation, new GridBagConstraints(1,0,1,1,0.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_dirBrowse, new GridBagConstraints(2,0,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 1, 0, 0), 0, 0));
		centerPanel.add(logLevelLabel, new GridBagConstraints(0,1,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_logLevel, new GridBagConstraints(1,1,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(fileLogLabel, new GridBagConstraints(0,2,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_fileLog, new GridBagConstraints(1,2,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(cleanLabel, new GridBagConstraints(0,3,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_clean, new GridBagConstraints(1,3,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(convertLabel, new GridBagConstraints(0,4,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_convertUsers, new GridBagConstraints(1,4,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(suppressLabel, new GridBagConstraints(0,5,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_suppressLog, new GridBagConstraints(1,5,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(printTraceLabel, new GridBagConstraints(0,6,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_printTrace, new GridBagConstraints(1,6,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(devModeLabel, new GridBagConstraints(0,7,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		centerPanel.add(_devMode, new GridBagConstraints(1,7,1,1,0.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(1, 0, 0, 0), 0, 0));
		southPanel.add(_logNotice);

		add(centerPanel, BorderLayout.NORTH);
		add(southPanel, BorderLayout.SOUTH);
		setSize(toolkit.getScreenColumns() - 4,toolkit.getScreenRows() - 7);
	}

	public void itemStateChanged(ItemEvent ie) {
		repaint();
	}

	private void updateLogLabel() {
		if (_fileLog.isSelected()) {
			_logNotice.setText("Build log will be saved to build.log in distribution directory");
		} else {
			_logNotice.setText("                                                              ");
		}
	}

	public void actionPerformed(ActionEvent ae) {
		if (ae.getSource().equals(_dirBrowse)) {
			JFileChooser installChooser = new JFileChooser(new File(_installLocation.getText()));
			installChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			int result = installChooser.showOpenDialog(this);
			if (result == JFileChooser.APPROVE_OPTION) {
				_installLocation.setText(installChooser.getSelectedFile().getPath());
			}
		}
	}

	public JTextField getInstallLocation() {
		return _installLocation;
	}

	protected int getLogIndex() {
		return _logList.indexOf(_logLevel.getSelectedItem());
	}

	protected boolean getFileLog() {
		return _fileLog.isSelected();
	}

	protected boolean getClean() {
		return _clean.isSelected();
	}

	protected boolean getConvertUsers() {
		return _convertUsers.isSelected();
	}

	protected boolean getSuppressLog() {
		return _suppressLog.isSelected();
	}

	protected boolean getPrintTrace() {
		return _printTrace.isSelected();
	}

	protected boolean getDevMode() {
		return _devMode.isSelected();
	}
}
