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
package org.drftpd.plugins.linkmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.event.ReloadEvent;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.event.ImmutableInodeHandle;
import org.drftpd.vfs.event.VirtualFileSystemInodeDeletedEvent;
import org.drftpd.vfs.event.VirtualFileSystemRenameEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author CyBeR
 * @version $Id: LinkManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class LinkManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(LinkManager.class);
	
	private CaseInsensitiveHashMap<String, Class<LinkType>> _typesMap;
	
	private ArrayList<LinkType> _links;
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
		loadConf();
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadConf();
    }	
    
    /*
     * Get the LinkManager Plugin
     */
    public static LinkManager getLinkManager() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof LinkManager) {
    			return (LinkManager) plugin;
    		}
    	}
    	throw new RuntimeException("LinkManager plugin is not loaded.");
    }    
	
    /*
	 * Returns the archive type corresponding with the .conf file
	 * and which Archive number the loop is on
	 */
	private LinkType getLinkType(int count, String type, Properties props) {
		LinkType linkType = null;
		Class<?>[] SIG = { Properties.class, int.class, String.class };
		
		if (!_typesMap.containsKey(type)) {
			// if we can't find one filter that will be enough to brake the whole chain.
            logger.error("Link Type: {} wasn't loaded.", type);
			
		} else {
			try {
				Class<LinkType> clazz = _typesMap.get(type);
				linkType = clazz.getConstructor(SIG).newInstance(props, count, type.toLowerCase());
			} catch (Exception e) {
                logger.error("Unable to load LinkType for section {}.type={}", count, type, e);
			}		
		}
		return linkType;	
	}
    
    private void initTypes() {
		CaseInsensitiveHashMap<String, Class<LinkType>> typesMap = new CaseInsensitiveHashMap<>();

		try {
			List<PluginObjectContainer<LinkType>> loadedTypes = CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.plugins.linkmanager", "LinkType", "ClassName", false);
			for (PluginObjectContainer<LinkType> container : loadedTypes) {
				String filterName = container.getPluginExtension().getParameter("TypeName").valueAsString();
				typesMap.put(filterName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.linkmanager extension point 'LinkType'",e);
		}
		_typesMap = typesMap;		
    }
    
    public void loadConf() {
    	initTypes();
		_links = new ArrayList<>();
		
		Properties _props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("linkmanager.conf");
    	int count = 1;
		String type;
		while ((type = PropertyHelper.getProperty(_props, count + ".type",null)) != null) {
			LinkType linkType = getLinkType(count,type,_props);
			if (linkType != null) {
				_links.add(linkType);
			}
			count++;					
		} 
    }
    
    /*
     * Returns a copy of all the current links types
     */
	public ArrayList<LinkType> getLinks() {
		return new ArrayList<>(_links);
	}
	
	/*
	 * Used for deleting links on wipe/rmd
	 */
	@EventSubscriber
	public void onVirtualFileSystemDeleteEvent(VirtualFileSystemInodeDeletedEvent vfsevent) {
		if (vfsevent.getInode().isDirectory()) {
			for (LinkType link : getLinks()) {
				if ((link.getDeleteOnContains("wipe")) || (link.getDeleteOnContains("rmd"))) {
					link.doDeleteLink(new DirectoryHandle(vfsevent.getInode().getPath()));
				}
			}			
		}
	}
	
	/*
	 * Used for changing links after Rename
	 */
	@EventSubscriber
	public void onVirtualFileSystemRenameEvent(VirtualFileSystemRenameEvent vfsevent) {
		ImmutableInodeHandle fromInode = vfsevent.getSource();
		if ((fromInode == null) || (!fromInode.isDirectory())) {
			// INode is not a directory
			return;
		}	
		
		InodeHandle toInode = vfsevent.getInode();
		if ((toInode == null) || (!toInode.isDirectory())) {
			// INode is not a directory
			return;
		}	

		DirectoryHandle fromDir = new DirectoryHandle(fromInode.getPath());
		DirectoryHandle toDir = (DirectoryHandle) toInode;

		for (LinkType link : getLinks()) {
			link.doRename(toDir,fromDir);
		}	
	}
}


