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
import charva.awt.event.ActionEvent;
import charva.awt.event.ActionListener;

import charvax.swing.JButton;
import charvax.swing.JFrame;
import charvax.swing.JMenu;
import charvax.swing.JMenuBar;
import charvax.swing.JMenuItem;
import charvax.swing.JPanel;
import charvax.swing.JTabbedPane;

import java.util.ArrayList;

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
public class ConsoleInstaller extends JFrame implements ActionListener {

	private JButton _buildButton;
	private ConfigPanel _configPanel;
	private PluginPanel _pluginPanel;
	private PluginRegistry _registry;

	public ConsoleInstaller(PluginRegistry registry) {
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
		_pluginPanel = new PluginPanel(registry);
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

		JPanel southPanel = new JPanel();
		FlowLayout southLayout = new FlowLayout();
		southLayout.setAlignment(FlowLayout.RIGHT);
		southPanel.setLayout(southLayout);
		southPanel.add(_buildButton);
		southPanel.add(cancelButton);

		contentPane.add(centerPanel, BorderLayout.CENTER);
		contentPane.add(southPanel, BorderLayout.SOUTH);

		setLocation(0, 0);
		setSize(80, 24);
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
			for (PluginData plugin : _pluginPanel.getTable().getPlugins()) {
				if (plugin.isSelected()) {
					toBuild.add(plugin);
				}
			}
			Logger.getRootLogger().setLevel(Level.toLevel(_configPanel.getLogLevel().getSelectedItem().toString()));
			if (_configPanel.getConsoleLog().isSelected()) {
				Logger.getRootLogger().addAppender(new ConsoleAppender(Logger.getRootLogger().getAppender("root").getLayout()));
			}
			PluginBuilder builder = new PluginBuilder(toBuild,_registry,_configPanel.getInstallLocation().getText());
			this.setVisible(false);
			builder.buildPlugins();
		}
	}

	private static void terminate() {
		System.exit(0);
	}
}
