/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.linkmanager.types.latestdir;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Properties;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;

import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.plugins.linkmanager.LinkManager;
import org.drftpd.plugins.linkmanager.LinkType;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.LinkHandle;
/**
 * @author freasy
 * @version $Id: LatestDirManager.java freasy $
 */

public class LatestDirManager implements PluginInterface {
	private LinkManager _linkmanager;
	
	private ArrayList<DirectoryHandle> _links;
	
	private HashMap<String, DirectoryHandle> _map;
	
	private int _count;
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
		loadManager();
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadManager();
    }
	
    private void loadManager() {
    	_linkmanager = LinkManager.getLinkManager();
		_links = new ArrayList<>();
		_map = new HashMap<>();
		Properties _props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("latestdir.conf");
		_count = Integer.parseInt(PropertyHelper.getProperty(_props, "maxcount","10"));
		for (LinkType link : _linkmanager.getLinks()) {
			if (link.getEventType().equals("latestdir")) {
				DirectoryHandle dir = new DirectoryHandle(link.getDirName(null));
				try {
					for(LinkHandle linkH : dir.getSortedLinksUnchecked())
					{
						_links.add(linkH.getTargetDirectoryUnchecked());
						_map.put(linkH.getTargetDirectoryUnchecked().getPath(), linkH.getTargetDirectoryUnchecked());
					}
					
					while(_links.size() > _count)
					{
						link.doDeleteLink(_links.get(0));
						_map.remove(_links.get(0).getPath());
						_links.remove(0);
					}
				} catch (Exception e) {}
			}
		}
    }
    
	/*
	* Handle the MKD command to make links
	*/
	@EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent event)
	{
		if(!event.getCommand().equalsIgnoreCase("MKD"))
			return;
		
		String rls = event.getDirectory().getPath().replaceAll(".*/(.*)", "$1");

		for (LinkType link : _linkmanager.getLinks()) {
			if (link.getEventType().equals("latestdir")) {
				if(!rls.matches(link.getExclude()))
				{
					if(_map.get(event.getDirectory().getPath()) == null)
					{
						link.doCreateLink(event.getDirectory());
						_links.add(event.getDirectory());
						_map.put(event.getDirectory().getPath(), event.getDirectory());
					}
					if(_links.size() > _count)
					{
						link.doDeleteLink(_links.get(0));
						_links.remove(0);
						_map.remove(event.getDirectory().getPath());
					}
				}
			}
		}
	}
}