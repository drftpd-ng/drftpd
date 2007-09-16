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
import java.awt.Component;
import java.util.ArrayList;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.ListSelectionModel;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.TableModelEvent;
import javax.swing.event.TableModelListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellRenderer;

import org.drftpd.tools.installer.PluginData;
import org.drftpd.tools.installer.PluginTools;
import org.java.plugin.registry.PluginRegistry;

/**
 * @author djb61
 * @version $Id$
 */
public class PluginPanel extends JPanel {

	private ArrayList<PluginData> _plugins;

	public PluginPanel(PluginRegistry registry) {
		BorderLayout pluginLayout = new BorderLayout();
		setLayout(pluginLayout);

		JTable table = createTable(registry);
		JScrollPane scrollPane = new JScrollPane(table);
		scrollPane.setBorder(new TitledBorder(new EtchedBorder(),"Select plugins"));
		add(scrollPane, BorderLayout.CENTER);
	}

	private JTable createTable(PluginRegistry registry) {
		_plugins = PluginTools.getPluginData(registry);
		String columnNames[] = {"Build","Plugin Name","Version"};
		JTable table = new JTable(new SwingTableModel(columnNames,_plugins,registry));
		table.setDefaultRenderer(Boolean.class, new PluginCellRenderer(_plugins,table.getDefaultRenderer(Boolean.class)));
		table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		table.getTableHeader().setReorderingAllowed(false);
		table.getColumnModel().getColumn(0).setMaxWidth(100);
		table.getColumnModel().getColumn(0).setResizable(false);
		table.getColumnModel().getColumn(2).setMaxWidth(100);
		table.getColumnModel().getColumn(2).setResizable(false);
		return table;
	}

	protected ArrayList<PluginData> getPlugins() {
		return _plugins;
	}
}

class PluginCellRenderer extends DefaultTableCellRenderer {

	private ArrayList<PluginData> _plugins;
	private TableCellRenderer _defaultRenderer;

	public PluginCellRenderer(ArrayList<PluginData> plugins, TableCellRenderer defaultRenderer) {
		super();
		_plugins = plugins;
		_defaultRenderer = defaultRenderer;
	}

	public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
		Component component = _defaultRenderer.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
		if (!_plugins.get(row).isModifiable()) {
			component.setEnabled(false);
		} else {
			component.setEnabled(true);
		}
		return component;
	}
}

class SwingTableModel extends AbstractTableModel implements TableModelListener {

	private String[] _columnNames;
	private ArrayList<PluginData> _plugins;
	private PluginRegistry _registry;
	private boolean _internalChange = false;

	public SwingTableModel(String[] columnNames, ArrayList<PluginData> plugins, PluginRegistry registry) {
		_columnNames = columnNames;
		_plugins = plugins;
		_registry = registry;
		addTableModelListener(this);
	}

	public int getColumnCount() {
		return _columnNames.length;
	}

	public int getRowCount() {
		return _plugins.size();
	}

	public String getColumnName(int col) {
		return _columnNames[col];
	}

	public Object getValueAt(int row, int col) {
		switch(col) {
		case 0: return new Boolean(_plugins.get(row).isSelected()); 
		case 1: return _plugins.get(row).getName();
		case 2: return _plugins.get(row).getDescriptor().getVersion().toString();
		default: return null;
		}
	}

	public Class<?> getColumnClass(int c) {
		return getValueAt(0, c).getClass();
	}

	public boolean isCellEditable(int row, int col) {
		if (col > 0) {
			return false;
		} else if (!_plugins.get(row).isModifiable()) {
			return false;
		} else {
			return true;
		}
	}

	public void setValueAt(Object value, int row, int col) {
		// Only have one editable column so can ignore any others
		if (col == 0) {
			_plugins.get(row).setSelected(value);
		}
		// If this is an internal change return to prevent a recursive loop
		if (_internalChange) {
			return;
		}
		fireTableCellUpdated(row, col);
	}

	public void tableChanged(TableModelEvent tme) {
		if (tme.getColumn() == 0 && tme.getFirstRow() == tme.getLastRow()) {
			PluginData selPlugin = _plugins.get(tme.getFirstRow());
			// Iterate over all entries to find any dependents we need to modify
			for (int i = 0; i < getRowCount(); i++) {
				PluginData dep = _plugins.get(i);
				if (selPlugin.isSelected()) {
					// Check for any entries the current entry depends on, if they aren't
					// selected then select them
					if (PluginTools.isDepends(selPlugin, dep, _registry)) {
						if (!dep.isSelected()) {
							dep.invertSelected();
							_internalChange = true;
							setValueAt(new Boolean(dep.isSelected()),i,0);
							_internalChange = false;
						}
					}
				} else {
					// Check for any entries that depend on the current entry, if they
					// are selected then deselect them
					if (PluginTools.isDepends(dep, selPlugin, _registry)) {
						if (dep.isSelected()) {
							dep.invertSelected();
							_internalChange = true;
							setValueAt(new Boolean(dep.isSelected()),i,0);
							_internalChange = false;
						}
					}
				}
			}
			// Notify the table that data has changed to make it redraw
			fireTableDataChanged();
		}
	}
}
