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
package org.drftpd.plugins.archive;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.PluginInterface;
import org.drftpd.PropertyHelper;
import org.drftpd.event.ReloadEvent;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.plugins.archive.archivetypes.ArchiveHandler;
import org.drftpd.plugins.archive.archivetypes.ArchiveType;
import org.drftpd.sections.SectionInterface;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

import java.util.*;

/**
 * @author CyBeR
 * @version $Id$
 */
public class Archive implements PluginInterface {
	private static final Logger logger = LogManager.getLogger(Archive.class);

	private Properties _props;

	private long _cycleTime;

	private HashSet<ArchiveHandler> _archiveHandlers = null;

	private TimerTask _runHandler = null;
	
	private CaseInsensitiveHashMap<String, Class<ArchiveType>> _typesMap;
	
	public Properties getProperties() {
		return _props;
	}
	
	public long getCycleTime() {
		return _cycleTime;
	}

	/*
	 * Returns the archive type corrisponding with the .conf file
	 * and which Archive number the loop is on
	 */
	public ArchiveType getArchiveType(int count, String type, SectionInterface sec, Properties props) {
		ArchiveType archiveType = null;
		Class<?>[] SIG = { Archive.class, SectionInterface.class, Properties.class, int.class };
		
		if (!_typesMap.containsKey(type)) {
			// if we can't find one filter that will be enought to brake the whole chain.
            logger.error("Archive Type: {} wasn't loaded.", type);
			
		} else {
	        if (!sec.getName().isEmpty()) {
				try {
					Class<ArchiveType> clazz = _typesMap.get(type);
					archiveType = clazz.getConstructor(SIG).newInstance(this, sec, props, count);
	
				} catch (Exception e) {
                    logger.error("Unable to load ArchiveType for section {}.{}", count, type, e);
				}		
	        } else {
                logger.error("Unable to load Section for Archive {}.{}", count, type);
	        }
		}
		return archiveType;	
	}

	/*
	 * Returns a list of the current archive types, as a copy.
	 * We don't want to allow modifications to this.
	 */
	public synchronized CaseInsensitiveHashMap<String, Class<ArchiveType>> getTypesMap() {
		return new CaseInsensitiveHashMap<>(_typesMap);
	}		
	
	/*
	 * Load the different Types of Archives specified in plugin.xml
	 */
	private void initTypes() {
		CaseInsensitiveHashMap<String, Class<ArchiveType>> typesMap = new CaseInsensitiveHashMap<>();

		try {
			List<PluginObjectContainer<ArchiveType>> loadedTypes =
				CommonPluginUtils.getPluginObjectsInContainer(this, "org.drftpd.plugins.archive", "ArchiveType", "ClassName", false);
			for (PluginObjectContainer<ArchiveType> container : loadedTypes) {
				String filterName = container.getPluginExtension().getParameter("TypeName").valueAsString();
				typesMap.put(filterName, container.getPluginClass());
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for org.drftpd.plugins.archive.archivetypes extension point 'ArchiveType'",e);
		}
		
		_typesMap = typesMap;
	}
	
	/*
	 * Reloads all the different archive's in .conf file
	 * Loops though each one and adds to the ArchiveHandler
	 */
	private void reload() {
		initTypes();
		
		_props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("archive.conf");
		_cycleTime = 60000 * Long.parseLong(PropertyHelper.getProperty(_props,"cycletime", "30").trim());
		
		if (_runHandler != null) {
			_runHandler.cancel();
			GlobalContext.getGlobalContext().getTimer().purge();
		}
		
		_runHandler = new TimerTask() {
			public void run() {
				
				int count = 1;
				
				String type;
				while ((type = PropertyHelper.getProperty(_props, count + ".type",null)) != null) {
					type = type.trim();
					SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().getSection(PropertyHelper.getProperty(_props, count + ".section","").trim());
					ArchiveType archiveType = getArchiveType(count,type,sec,_props);
					if (archiveType != null) {
						new ArchiveHandler(archiveType).start();
					}
					count++;					
				} 
			}
		};
		try {
			GlobalContext.getGlobalContext().getTimer().schedule(_runHandler, _cycleTime, _cycleTime);
		} catch (IllegalStateException e) {
			// Timer Already Canceled
		}
	}

	/*
	 * This Removes archive handler from current archives in use
	 */
	public synchronized boolean removeArchiveHandler(ArchiveHandler handler) {
		return _archiveHandlers.remove(handler);
	}

	/*
	 * Returns all the current ArchiveHandlers
	 */
	public Collection<ArchiveHandler> getArchiveHandlers() {
		return Collections.unmodifiableCollection(_archiveHandlers);
	}

	/*
	 * Adds a specfic ArchiveHandle to the list.  Makes sure directory already isn't added.
	 */
	public synchronized void addArchiveHandler(ArchiveHandler handler) throws DuplicateArchiveException {
		checkPathForArchiveStatus(handler.getArchiveType().getDirectory().getPath());
		_archiveHandlers.add(handler);
	}

	/*
	 * This checks to see if the current directory is already queued to be archived.
	 * Throws DuplicateArchive expcetion if it is.
	 */
	public synchronized void checkPathForArchiveStatus(String handlerPath) throws DuplicateArchiveException {
        for (ArchiveHandler ah : _archiveHandlers) {
            String ahPath = ah.getArchiveType().getDirectory().getPath();

            if (ahPath.length() > handlerPath.length()) {
                if (ahPath.startsWith(handlerPath)) {
                    throw new DuplicateArchiveException(ahPath + " is already being archived");
                }
            } else {
                if (handlerPath.startsWith(ahPath)) {
                    throw new DuplicateArchiveException(handlerPath + " is already being archived");
                }
            }
        }
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		reload();
	}

	public void startPlugin() {
		// Subscribe to events
		AnnotationProcessor.process(this);
		logger.info("Archive plugin loaded successfully");
		_archiveHandlers = new HashSet<>();
		reload();
	}

	public void stopPlugin(String reason) {
		if (_runHandler != null) {
			_runHandler.cancel();
			GlobalContext.getGlobalContext().getTimer().purge();
		}
		AnnotationProcessor.unprocess(this);
		logger.info("Archive plugin unloaded successfully");
	}
}
