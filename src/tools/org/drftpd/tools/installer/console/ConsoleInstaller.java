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

import charva.awt.BorderLayout;
import charva.awt.Container;
import charva.awt.FlowLayout;
import charva.awt.Toolkit;
import charva.awt.event.ActionEvent;
import charva.awt.event.ActionListener;
import charva.awt.event.KeyEvent;
import charva.awt.event.KeyListener;
import charvax.swing.*;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.tools.installer.InstallerConfig;
import org.drftpd.tools.installer.PluginBuilder;
import org.drftpd.tools.installer.PluginData;
import org.java.plugin.registry.PluginRegistry;

import java.io.IOException;
import java.io.PipedInputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author djb61
 * @version $Id$
 */
public class ConsoleInstaller extends JFrame implements ActionListener, KeyListener {

	private static final Logger logger = LogManager.getLogger(ConsoleInstaller.class);
	private static final Toolkit toolkit = Toolkit.getDefaultToolkit();

	private JButton _buildButton;
	private JButton _cleanButton;
	private JButton _exitButton;
	private JButton _selectAllButton;
	private JTabbedPane _tabbedPane;
	private ConfigPanel _configPanel;
	private PluginPanel _pluginPanel;
	private PluginRegistry _registry;
	private InstallerConfig _config;

	public ConsoleInstaller(PluginRegistry registry, InstallerConfig config) {
		super("DrFTPD Installer");
		_registry = registry;
		_config = config;
		Container contentPane = getContentPane();
		contentPane.setLayout(new BorderLayout());

		JMenuBar menubar = new JMenuBar();
		JMenu jMenuFile = new JMenu("File");
		jMenuFile.setMnemonic('F');

		JMenuItem jMenuItemFileExit = new JMenuItem("Exit", 'x');
		jMenuItemFileExit.addActionListener(this);
		jMenuFile.add(jMenuItemFileExit);

		menubar.add(jMenuFile);
		setJMenuBar(menubar);

		_tabbedPane = new JTabbedPane();
		_configPanel = new ConfigPanel(_config);
		_tabbedPane.addTab("Config", null, _configPanel, "F1");
		_pluginPanel = new PluginPanel(registry,_config);
		_tabbedPane.addTab("Plugins", null, _pluginPanel, "F2");

		JPanel centerPanel = new JPanel();
		BorderLayout centerLayout = new BorderLayout();
		centerPanel.setLayout(centerLayout);
		centerPanel.add(_tabbedPane, BorderLayout.CENTER);

		_exitButton = new JButton();
		_exitButton.setText("Exit");
		_exitButton.addActionListener(e -> terminate());
		_buildButton = new JButton();
		_buildButton.setText("Build");
		_buildButton.addActionListener(this);
		_cleanButton = new JButton();
		_cleanButton.setText("Clean");
		_cleanButton.addActionListener(this);
		_selectAllButton = new JButton();
		_selectAllButton.setText("Select All");
		_selectAllButton.addActionListener(this);

		JPanel southPanel = new JPanel();
		BorderLayout southLayout = new BorderLayout();
		southPanel.setLayout(southLayout);
		JPanel southEastPanel = new JPanel();
		FlowLayout southEastLayout = new FlowLayout();
		southEastLayout.setAlignment(FlowLayout.RIGHT);
		southEastPanel.setLayout(southEastLayout);
		southEastPanel.add(_buildButton);
		southEastPanel.add(_cleanButton);
		southEastPanel.add(_exitButton);
		JPanel southWestPanel = new JPanel();
		FlowLayout southWestLayout = new FlowLayout();
		southWestLayout.setAlignment(FlowLayout.LEFT);
		southWestPanel.setLayout(southWestLayout);
		southWestPanel.add(_selectAllButton);
		southPanel.add(southWestPanel, BorderLayout.WEST);
		southPanel.add(southEastPanel, BorderLayout.EAST);

		contentPane.add(centerPanel, BorderLayout.CENTER);
		contentPane.add(southPanel, BorderLayout.SOUTH);

		addKeyListener(this);

		setLocation(0, 0);
		setSize(toolkit.getScreenColumns(),toolkit.getScreenRows());
		validate();
	}

	public void actionPerformed(ActionEvent ae) {
		String actionCommand = ae.getActionCommand();
		if (actionCommand.equals("Exit")) {
			terminate();
		}
		Object actionSource = ae.getSource();
		if (actionSource.equals(_buildButton) || actionSource.equals(_cleanButton)) {
			_config.setInstallDir(_configPanel.getInstallLocation().getText());
			_config.setLogLevel(_configPanel.getLogIndex());
			_config.setFileLogging(_configPanel.getFileLog());
			_config.setClean(_configPanel.getClean());
			_config.setConvertUsers(_configPanel.getConvertUsers());
			_config.setSuppressLog(_configPanel.getSuppressLog());
			_config.setPrintTrace(_configPanel.getPrintTrace());
			_config.setDevMode(_configPanel.getDevMode());
			HashMap<String,Boolean> selPlugins = new HashMap<>();
			ArrayList<PluginData> toBuild = new ArrayList<>();
			for (PluginData plugin : _pluginPanel.getPlugins()) {
				selPlugins.put(plugin.getName(), plugin.isSelected());
				if (plugin.isSelected()) {
					toBuild.add(plugin);
				}
			}
			_config.setPluginSelections(selPlugins);
			// only save current config when building, not when just cleaning
			if (actionSource.equals(_buildButton)) {
				try {
					_config.writeToDisk();
				} catch (IOException e) {
					logger.warn("Unable to write current config to build.conf",e);
				}
			}
			PipedInputStream logInput = new PipedInputStream();
			LogWindow logWindow = new LogWindow(logInput,_config,toBuild.size(),actionSource.equals(_cleanButton));
			PluginBuilder builder = new PluginBuilder(toBuild,_registry,logInput,_config,logWindow,actionSource.equals(_cleanButton));
			logWindow.setBuilder(builder);
			try {
				logWindow.init();
			} catch (IOException e) {
				System.out.println(e);
			}
		}
		if (actionSource.equals(_selectAllButton)) {
			_pluginPanel.selectAllPlugins();
		}
	}

	public void keyPressed(KeyEvent ke) {
		if (ke.getKeyCode() == KeyEvent.VK_F1) {
			_tabbedPane.setSelectedIndex(0);
		} else if (ke.getKeyCode() == KeyEvent.VK_F2) {
			_tabbedPane.setSelectedIndex(1);
		}
	}

	public void keyReleased(KeyEvent ke) {
		// Not used, but needed to implement the KeyListener interface
	}

	public void keyTyped(KeyEvent ke) {
		// Not used, but needed to implement the KeyListener interface
	}

	private static void terminate() {
		// Print a couple of blank lines so that the shell prompt returns on a new line when we exit
		System.out.println("");
		System.out.println("");
		System.exit(0);
	}
}
