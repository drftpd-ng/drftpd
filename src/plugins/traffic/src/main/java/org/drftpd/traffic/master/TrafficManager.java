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
package org.drftpd.traffic.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.dataconnection.event.SlowTransferEvent;
import org.drftpd.master.event.ReloadEvent;
import org.reflections.Reflections;

import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;

/**
 * @author CyBeR
 * @version $Id: TrafficManager.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TrafficManager implements PluginInterface {
    private static final Logger logger = LogManager.getLogger(TrafficManager.class);

    private CaseInsensitiveHashMap<String, Class<? extends TrafficType>> _typesMap;

    private ArrayList<TrafficType> _traffictypes;

    @Override
    public void startPlugin() {
        AnnotationProcessor.process(this);
        loadConf();
        logger.debug("Started TrafficManager Plugin");
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
        Class<?>[] SIG = {Properties.class, int.class, String.class};

        if (!_typesMap.containsKey(type)) {
            // if we can't find one filter that will be enough to brake the whole chain.
            logger.error("Traffic Type: {} wasn't loaded.", type);

        } else {
            try {
                Class<? extends TrafficType> clazz = _typesMap.get(type);
                trafficType = clazz.getConstructor(SIG).newInstance(props, count, type.toLowerCase());
            } catch (Exception e) {
                logger.error("Unable to load TrafficType for section {}.type={}", count, type, e);
            }
        }
        return trafficType;
    }

    private void initTypes() {
        CaseInsensitiveHashMap<String, Class<? extends TrafficType>> typesMap = new CaseInsensitiveHashMap<>();

        // TODO [DONE] @k2r Load init types
        Set<Class<? extends TrafficType>> trafficTypes = new Reflections("org.drftpd")
                .getSubTypesOf(TrafficType.class);
        for (Class<? extends TrafficType> trafficType : trafficTypes) {
            String simpleName = trafficType.getSimpleName().replace("Traffic", "");
            typesMap.put(simpleName, trafficType);
        }
        _typesMap = typesMap;
    }


    public void loadConf() {
        initTypes();
        _traffictypes = new ArrayList<>();
        Properties props = ConfigLoader.loadPluginConfig("traffic.conf");
        int count = 1;
        String type;
        while ((type = PropertyHelper.getProperty(props, count + ".type", null)) != null) {
            TrafficType trafficType = getTrafficType(count, type, props);
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
                    trafficType.doAction(event.getUser(), event.getFile(), event.isStor(), event.getMinSpeed(), event.getSpeed(), event.getTransfered(), event.getConn(), event.getSlaveName());
                    // Return since we only want one action to run
                    return;
                }
            }
        }
    }
}


