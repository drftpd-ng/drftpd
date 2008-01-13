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
import java.awt.Container;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.tools.installer.PluginBuilder;
import org.drftpd.tools.installer.PluginData;
import org.java.plugin.registry.PluginRegistry;

/**
 * @author djb61
 * @version $Id$
 */
public class SwingInstaller extends JFrame implements ActionListener {

	private JButton _buildButton;
	private JButton _selectAllButton;
	private ConfigPanel _configPanel;
	private PluginPanel _pluginPanel;
	private PluginRegistry _registry;

	public SwingInstaller(PluginRegistry registry) {
		super("DrFTPD Installer");
		_registry = registry;
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
		_configPanel = new ConfigPanel();
		tabbedPane.add(_configPanel, "Config");
		_pluginPanel = new PluginPanel(registry,tabbedPane);
		tabbedPane.add(_pluginPanel, "Plugins");

		JPanel centerPanel = new JPanel();
		BorderLayout centerLayout = new BorderLayout();
		centerPanel.setLayout(centerLayout);
		centerPanel.add(tabbedPane, BorderLayout.CENTER);

		JButton cancelButton = new JButton();
		cancelButton.setText("Cancel");
		cancelButton.addActionListener(new ActionListener() {
			public void actionPerformed(ActionEvent e) {
				terminate();
			}
		});
		_buildButton = new JButton();
		_buildButton.setText("Build");
		_buildButton.addActionListener(this);
		_selectAllButton = new JButton();
		_selectAllButton.setText("Select All");
		_selectAllButton.addActionListener(this);

		JPanel southPanel = new JPanel();
		GridLayout southLayout = new GridLayout(1,2);
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
		southEastPanel.add(cancelButton);
		southPanel.add(southWestPanel);
		southPanel.add(southEastPanel);

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
		if (actionSource.equals(_buildButton)) {
			ArrayList<PluginData> toBuild = new ArrayList<PluginData>();
			for (PluginData plugin : _pluginPanel.getPlugins()) {
				if (plugin.isSelected()) {
					toBuild.add(plugin);
				}
			}
			Logger.getRootLogger().setLevel(Level.toLevel(_configPanel.getLogLevel().getSelectedItem().toString()));
			if (_configPanel.getConsoleLog().isSelected()) {
				Logger.getRootLogger().addAppender(new ConsoleAppender(Logger.getRootLogger().getAppender("root").getLayout()));
			}
			PluginBuilder builder = new PluginBuilder(toBuild,_registry,_configPanel.getInstallLocation().getText());
			this.dispose();
			builder.buildPlugins();
		}
		if (actionSource.equals(_selectAllButton)) {
			_pluginPanel.selectAllPlugins();
		}
	}

	private static void terminate() {
		System.exit(0);
	}
}
