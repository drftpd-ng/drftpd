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
package org.drftpd.plugins.trialmanager;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.event.ReloadEvent;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

/**
 * @author CyBeR
 * @version $Id: TrialManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrialManager implements PluginInterface {
	private static final Logger logger = Logger.getLogger(TrialManager.class);
	
	private CaseInsensitiveHashMap<String, Class<TrialType>> _typesMap;
	
	private ArrayList<TrialType> _trials;
	
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
     * Get the TrialManager Plugin
     */
    public static TrialManager getTrialManager() {
    	for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
    		if (plugin instanceof TrialManager) {
    			return (TrialManager) plugin;
    		}
    	}
    	throw new RuntimeException("TrialManager plugin is not loaded.");
    }    
	
    /*
	 * Returns the Trial type corresponding with the .conf file
	 * and which Trial number the loop is on
	 */
	private TrialType getTrialType(int count, String type, Properties props) {
		TrialType trialType = null;
		Class<?>[] SIG = { Properties.class, int.class, String.class };
		
		if (!_typesMap.containsKey(type)) {
			// if we can't find one filter that will be enough to brake the whole chain.
			logger.error("Trial Type: " + type + " wasn't loaded.");
			
		} else {
			try {
				Class<TrialType> clazz = _typesMap.get(type);
				trialType = clazz.getConstructor(SIG).newInstance(new Object[] { props, count, type.toLowerCase() });
			} catch (Exception e) {
				logger.error("Unable to load TrialType for section " + count + ".type=" + type, e);
			}		
		}
		return trialType;	
	}
    
    private void initTypes() {
		CaseInsensitiveHashMap<String, Class<TrialType>> typesMap = new CaseInsensitiveHashMap<String, Class<TrialType>>();

		try {
			List<PluginObjectContainer<TrialType>> loadedTypes = CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.plugins.trialmanager", "TrialType", "ClassName", false);
			for (PluginObjectContainer<TrialType> container : loadedTypes) {
				String filterName = container.getPluginExtension().getParameter("TypeName").valueAsString();
				typesMap.put(filterName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.trialmanager extension point 'TrialType'",e);
		}
		_typesMap = typesMap;		
    }
    
    public void loadConf() {
    	initTypes();
		_trials = new ArrayList<TrialType>();
		
		Properties _props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("trialmanager.conf");
    	int count = 1;
		String type;
		while ((type = PropertyHelper.getProperty(_props, count + ".type",null)) != null) {
			TrialType trialType = getTrialType(count,type,_props);
			if (trialType != null) {
				_trials.add(trialType);
			}
			count++;					
		} 
    }
    
    /*
     * Returns a copy of all the current trial types
     */
	public ArrayList<TrialType> getTrials() {
		return new ArrayList<TrialType>(_trials);
	}
	
}


