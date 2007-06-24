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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;

import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

import org.apache.log4j.Logger;

/**
 * @author djb61
 * @version $Id$
 */
public class ConfigPanel extends JPanel implements ItemListener {

	private JTextField _installLocation;
	private JComboBox _logLevel;
	private JCheckBox _consoleLog;

	public ConfigPanel() {
		setLayout(new BorderLayout());
		JPanel centerPanel = new JPanel();
		centerPanel.setLayout(new GridBagLayout());
		JPanel southPanel = new JPanel();
		southPanel.setLayout(new FlowLayout());

		JLabel installLabel = new JLabel();
		installLabel.setText("Install location: ");
		_installLocation = new JTextField(30);
		_installLocation.setText(System.getProperty("user.dir"));

		JLabel logLevelLabel = new JLabel();
		logLevelLabel.setText("Build log level: ");
		_logLevel = new JComboBox();
		_logLevel.addItem(new String("ALL"));
		_logLevel.addItem(new String("TRACE"));
		_logLevel.addItem(new String("DEBUG"));
		_logLevel.addItem(new String("INFO"));
		_logLevel.addItem(new String("WARN"));
		_logLevel.addItem(new String("ERROR"));
		_logLevel.addItem(new String("FATAL"));
		_logLevel.addItem(new String("OFF"));
		_logLevel.setMaximumRowCount(8);
		_logLevel.addItemListener(this);
		_logLevel.setSelectedItem(Logger.getRootLogger().getLevel().toString());

		JLabel consoleLogLabel = new JLabel();
		consoleLogLabel.setText("Enable console logging: ");
		_consoleLog = new JCheckBox();

		JLabel logNotice = new JLabel();
		logNotice.setText("Build log will be saved to build.log in distribution directory");

		centerPanel.add(installLabel, new GridBagConstraints(0,0,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		centerPanel.add(_installLocation, new GridBagConstraints(1,0,1,1,100.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		centerPanel.add(logLevelLabel, new GridBagConstraints(0,1,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		centerPanel.add(_logLevel, new GridBagConstraints(1,1,1,1,100.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		centerPanel.add(consoleLogLabel, new GridBagConstraints(0,2,1,1,100.0,0.0
				,GridBagConstraints.NORTHEAST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		centerPanel.add(_consoleLog, new GridBagConstraints(1,2,1,1,100.0,0.0
				,GridBagConstraints.NORTHWEST, GridBagConstraints.NONE, new Insets(10, 0, 0, 0), 0, 0));
		southPanel.add(logNotice);

		add(centerPanel, BorderLayout.NORTH);
		add(southPanel, BorderLayout.SOUTH);
	}

	public void itemStateChanged(ItemEvent ie) {
		repaint();
	}

	protected JTextField getInstallLocation() {
		return _installLocation;
	}

	protected JComboBox getLogLevel() {
		return _logLevel;
	}

	public JCheckBox getConsoleLog() {
		return _consoleLog;
	}
}
