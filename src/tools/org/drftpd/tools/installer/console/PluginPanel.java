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
import charva.awt.Color;
import charva.awt.Dimension;
import charva.awt.Toolkit;
import charva.awt.event.KeyEvent;
import charvax.swing.*;
import charvax.swing.border.LineBorder;
import charvax.swing.border.TitledBorder;
import org.drftpd.tools.installer.InstallerConfig;
import org.drftpd.tools.installer.PluginData;
import org.drftpd.tools.installer.PluginTools;
import org.java.plugin.registry.Documentation;
import org.java.plugin.registry.PluginDescriptor;
import org.java.plugin.registry.PluginRegistry;

import java.util.ArrayList;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginPanel extends JPanel {

	private static final Toolkit toolkit = Toolkit.getDefaultToolkit();

	private ConsoleTable _table;
	private ArrayList<PluginData> _plugins;

	public PluginPanel(PluginRegistry registry, InstallerConfig config) {
		_plugins = PluginTools.getPluginData(registry);
		for (PluginData plugin : _plugins) {
			Boolean sel = config.getPluginSelections().get(plugin.getName());
			if (sel != null) {
				plugin.setSelected(sel);
			}
		}
		BorderLayout pluginLayout = new BorderLayout();
		setLayout(pluginLayout);
		int availHeight = toolkit.getScreenRows() - 11;
		int descHeight = availHeight / 4;
		if (descHeight < 3) {
			descHeight = 3;
		}
		int tableHeight = availHeight - descHeight;

		JTextArea desc = new JTextArea("No plugin selected",descHeight,toolkit.getScreenColumns() - 6);
		desc.setLineWrap(true);
		desc.setEditable(false);
		desc.setWrapStyleWord(true);
		JScrollPane descPane = new JScrollPane(desc);
		TitledBorder descBorder = new TitledBorder(new LineBorder(Color.white));
		descBorder.setTitle("Plugin Description");
		descPane.setViewportBorder(descBorder);
		_table = createTable(registry,desc,tableHeight);
		JScrollPane scrollPane = new JScrollPane(_table);
		TitledBorder pluginBorder = new TitledBorder(new LineBorder(Color.white));
		pluginBorder.setTitle("Select plugins");
		scrollPane.setViewportBorder(pluginBorder);
		add(scrollPane, BorderLayout.CENTER);
		add(descPane, BorderLayout.SOUTH);
	}

	private ConsoleTable createTable(PluginRegistry registry, JTextArea desc, int height) {
		String columnNames[] = {"Build","Plugin Name","Version"};
		String tableData[][] = new String[_plugins.size()][3];
		int count = 0;
		for (PluginData plugin : _plugins) {
			tableData[count][0] = plugin.isSelected() ? "yes" : "no";
			tableData[count][1] = plugin.getName();
			tableData[count][2] = plugin.getDescriptor().getVersion().toString();
			count++;
		}
		ConsoleTable table = new ConsoleTable(tableData,columnNames,_plugins,registry,desc);
		table.setColumnSelectionAllowed(false);
		table.setRowSelectionAllowed(true);
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.setPreferredScrollableViewportSize(new Dimension(toolkit.getScreenColumns() - 6, height));
		return table;
	}

	protected ConsoleTable getTable() {
		return _table;
	}

	protected ArrayList<PluginData> getPlugins() {
		return _plugins;
	}

	protected void selectAllPlugins() {
		ConsoleTable table = getTable();
		int i = 0;
		for (PluginData plugin : getPlugins()) {
			if (!plugin.isSelected()) {
				plugin.invertSelected();
				table.setValueAt(plugin.isSelected() ? "yes" : "no",i,0);
			}
			i++;
		}
	}
}

class ConsoleTable extends JTable {

	private ArrayList<PluginData> _plugins;
	private PluginRegistry _registry;
	private int _numRows;
	private int _curRow = 0;
	private JTextArea _desc;

	public ConsoleTable(Object[][] data, Object[] columnNames, ArrayList<PluginData> plugins, PluginRegistry registry, JTextArea desc) {
		super(data,columnNames);
		_plugins = plugins;
		_registry = registry;
		_numRows = plugins.size() - 1;
		_desc = desc;
		updatePluginDescription();
	}

	public void processKeyEvent(KeyEvent ke) {
		super.processKeyEvent(ke);
		if (ke.getKeyCode() == KeyEvent.VK_ENTER) {
			PluginData selPlugin = _plugins.get(_curRow);
			if (selPlugin.isModifiable()) {
				_plugins.get(_curRow).invertSelected();
				setValueAt(selPlugin.isSelected() ? "yes" : "no",_curRow,0);
				// Iterate over all entries to find any dependents we need to modify
				for (int i = 0; i < _numRows+1; i++) {
					PluginData dep = _plugins.get(i);
					if (selPlugin.isSelected()) {
						// Check for any entries the current entry depends on, if they aren't
						// selected then select them
						if (PluginTools.isDependsInclImpl(selPlugin, dep, _registry)) {
							if (!dep.isSelected()) {
								dep.invertSelected();
								setValueAt(dep.isSelected() ? "yes" : "no",i,0);
							}
						}
					} else {
						// Check for any entries that depend on the current entry, if they
						// are selected then deselect them
						if (PluginTools.isDependsInclImpl(dep, selPlugin, _registry)) {
							if (dep.isSelected()) {
								dep.invertSelected();
								setValueAt(dep.isSelected() ? "yes" : "no",i,0);
							}
						}
					}
				}	
			}
		} else if (ke.getKeyCode() == KeyEvent.VK_UP) {
			if (_curRow > 0) {
				_curRow--;
				updatePluginDescription();
			}
		} else if (ke.getKeyCode() == KeyEvent.VK_DOWN) {
			if (_curRow < _numRows) {
				_curRow++;
				updatePluginDescription();
			}
		}
	}

	private void updatePluginDescription() {
		PluginData selPlugin = _plugins.get(_curRow);
		Documentation<PluginDescriptor> doc = selPlugin.getDescriptor().getDocumentation();
		if (doc != null) {
			_desc.setText(doc.getText().replaceAll("\r\n|\n|\r"," "));
		} else {
			// The current selection has no docs, so clear the text for the previous one
			_desc.setText("This plugin has no description set, please contact the author.");
		}
		_desc.repaint();
	}
}
