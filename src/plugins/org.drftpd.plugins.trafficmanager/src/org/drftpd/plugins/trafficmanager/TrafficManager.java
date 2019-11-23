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
package org.drftpd.plugins.trafficmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.commands.dataconnection.event.SlowTransferEvent;
import org.drftpd.event.ReloadEvent;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author CyBeR
 * @version $Id: TrafficManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficManager implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(TrafficManager.class);

	private CaseInsensitiveHashMap<String, Class<TrafficType>> _typesMap;
	
	private ArrayList<TrafficType> _traffictypes;
	
	@Override
	public void startPlugin() {
		AnnotationProcessor.process(this);
		loadConf();
		logger.debug("Strated TrafficManager Plugin");
	}

	@Override
	public void stopPlugin(String reason) {
		AnnotationProcessor.unprocess(this);
		_traffictypes = new ArrayList<>();
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadConf();
    }	
    
    public static TrafficManager getTrafficManager() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof TrafficManager) {
    			return (TrafficManager) plugin;
    		}
    	}
    	throw new RuntimeException("TrafficManager plugin is not loaded.");
    }  
    
    /*
	 * Returns the Traffic type corresponding with the .conf file
	 * and which Traffic number the loop is on
	 */
	private TrafficType getTrafficType(int count, String type, Properties props) {
		TrafficType trafficType = null;
		Class<?>[] SIG = { Properties.class, int.class, String.class };
		
		if (!_typesMap.containsKey(type)) {
			// if we can't find one filter that will be enough to brake the whole chain.
            logger.error("Traffic Type: {} wasn't loaded.", type);
			
		} else {
			try {
				Class<TrafficType> clazz = _typesMap.get(type);
				trafficType = clazz.getConstructor(SIG).newInstance(props, count, type.toLowerCase());
			} catch (Exception e) {
                logger.error("Unable to load TrafficType for section {}.type={}", count, type, e);
			}		
		}
		return trafficType;	
	}
	
	private void initTypes() {
		CaseInsensitiveHashMap<String, Class<TrafficType>> typesMap = new CaseInsensitiveHashMap<>();

		try {
			List<PluginObjectContainer<TrafficType>> loadedTypes = CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.plugins.trafficmanager", "TrafficType", "ClassName", false);
			for (PluginObjectContainer<TrafficType> container : loadedTypes) {
				String filterName = container.getPluginExtension().getParameter("TypeName").valueAsString();
				typesMap.put(filterName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.trafficmanager extension point 'TrafficType'",e);
		}
		_typesMap = typesMap;		
    }
    
	
	public void loadConf() {
    	initTypes();
		_traffictypes = new ArrayList<>();
		
		Properties _props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("trafficmanager.conf");
    	int count = 1;
		String type;
		while ((type = PropertyHelper.getProperty(_props, count + ".type",null)) != null) {
			TrafficType trafficType = getTrafficType(count,type,_props);
			if (trafficType != null) {
				_traffictypes.add(trafficType);
			}
			count++;					
		} 
    }
	
    /*
     * Returns a copy of the current traffic types
     */
    public ArrayList<TrafficType> getTrafficTypes() {
    	return new ArrayList<>(_traffictypes);
    }

	/*
	 * Used for receiving a slow transfer event
	 */
	@EventSubscriber
	public void onSlowTransferEvent(SlowTransferEvent event) {
		for (TrafficType trafficType : getTrafficTypes()) {
			// Check if allowed to run on Upload/Download
			if ((event.isStor() && trafficType.getUpload()) || (!event.isStor() && trafficType.getDownload())) {
				// Check if Include/Exclude path are allowed
				// And check perms are correct
				if ((trafficType.checkInclude(event.getFile().getParent().getPath())) && (!trafficType.checkExclude(event.getFile().getParent().getPath())) && (trafficType.getPerms().check(event.getUser()))) {
					trafficType.doAction(event.getUser(),event.getFile(),event.isStor(),event.getMinSpeed(),event.getSpeed(),event.getTransfered(),event.getConn(),event.getSlaveName());
					// Return since we only want one action to run
					return;
				}
			}
		}
	}
}


