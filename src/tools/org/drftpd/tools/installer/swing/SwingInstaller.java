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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.tools.installer.InstallerConfig;
import org.drftpd.tools.installer.PluginBuilder;
import org.drftpd.tools.installer.PluginBuilderThread;
import org.drftpd.tools.installer.PluginData;
import org.java.plugin.registry.PluginRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowEvent;
import java.io.IOException;
import java.io.PipedInputStream;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class SwingInstaller extends JFrame implements ActionListener {

	private static final Logger logger = LogManager.getLogger(SwingInstaller.class);

	private JButton _buildButton;
	private JButton _cleanButton;
	private JButton _exitButton;
	private JButton _selectAllButton;
	private ConfigPanel _configPanel;
	private PluginPanel _pluginPanel;
	private PluginRegistry _registry;
	private InstallerConfig _config;

	public SwingInstaller(PluginRegistry registry, InstallerConfig config) {
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

		JTabbedPane tabbedPane = new JTabbedPane();
		_configPanel = new ConfigPanel(_config);
		tabbedPane.add(_configPanel, "Config");
		_pluginPanel = new PluginPanel(registry,tabbedPane,_config);
		tabbedPane.add(_pluginPanel, "Plugins");

		JPanel centerPanel = new JPanel();
		BorderLayout centerLayout = new BorderLayout();
		centerPanel.setLayout(centerLayout);
		centerPanel.add(tabbedPane, BorderLayout.CENTER);

		_exitButton = new JButton();
		_exitButton.setText("Exit");
		_exitButton.setPreferredSize(new Dimension(100,25));
		_exitButton.addActionListener(e -> terminate());
		_buildButton = new JButton();
		_buildButton.setText("Build");
		_buildButton.setPreferredSize(new Dimension(100,25));
		_buildButton.addActionListener(this);
		_cleanButton = new JButton();
		_cleanButton.setText("Clean");
		_cleanButton.setPreferredSize(new Dimension(100,25));
		_cleanButton.addActionListener(this);
		_selectAllButton = new JButton();
		_selectAllButton.setText("Select All");
		_selectAllButton.setPreferredSize(new Dimension(100,25));
		_selectAllButton.addActionListener(this);

		JPanel southPanel = new JPanel();
		GridBagLayout southLayout = new GridBagLayout();
		southPanel.setLayout(southLayout);
		JPanel southWestPanel = new JPanel();
		FlowLayout southWestLayout = new FlowLayout();
		southWestLayout.setAlignment(FlowLayout.LEFT);
		southWestPanel.setLayout(southWestLayout);
		southWestPanel.add(_selectAllButton);
		JPanel southEastPanel = new JPanel();
		FlowLayout southEastLayout = new FlowLayout();
		southEastLayout.setAlignment(FlowLayout.RIGHT);
		southEastPanel.setLayout(southEastLayout);
		southEastPanel.add(_buildButton);
		southEastPanel.add(_cleanButton);
		southEastPanel.add(_exitButton);
		southPanel.add(southWestPanel, new GridBagConstraints(0,0,1,1,0.0,0.0
				,GridBagConstraints.WEST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));
		southPanel.add(southEastPanel, new GridBagConstraints(1,0,1,1,100.0,0.0
				,GridBagConstraints.EAST,GridBagConstraints.NONE,new Insets(0,0,0,0),0,0));

		contentPane.add(centerPanel, BorderLayout.CENTER);
		contentPane.add(southPanel, BorderLayout.SOUTH);

		setSize(600, 500);
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
			_config.setLogLevel(_configPanel.getLogLevel().getSelectedIndex());
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
			//Logger.getRootLogger().setLevel(Level.toLevel(_configPanel.getLogLevel().getSelectedItem().toString()));
			// only save current config when building, not when just cleaning
			if (actionSource.equals(_buildButton)) {
				try {
					_config.writeToDisk();
				} catch (IOException e) {
					logger.warn("Unable to write current config to build.conf",e);
				}
			}
			PipedInputStream logInput = new PipedInputStream();
			LogWindow logWindow = new LogWindow(logInput,_buildButton,_cleanButton,_selectAllButton,_exitButton,_config,toBuild.size(),actionSource.equals(_cleanButton));
			PluginBuilder builder = new PluginBuilder(toBuild,_registry,logInput,_config,logWindow,actionSource.equals(_cleanButton));
			try {
				logWindow.init();
				new Thread(new PluginBuilderThread(builder)).start();
			} catch (IOException e) {
				System.out.println(e);
			}
		}
		if (actionSource.equals(_selectAllButton)) {
			_pluginPanel.selectAllPlugins();
		}
	}

	private static void terminate() {
		System.exit(0);
	}

	protected void processWindowEvent(WindowEvent e) {
		super.processWindowEvent(e);
		if (e.getID() == WindowEvent.WINDOW_CLOSING) {
			terminate();
		}
	}
}
