/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.archive.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.archive.master.archivetypes.ArchiveHandler;
import org.drftpd.archive.master.archivetypes.ArchiveType;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.misc.CaseInsensitiveHashMap;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.reflections.Reflections;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

/**
 * @author CyBeR
 * @version $Id$
 */
public class Archive implements PluginInterface {

    private static final Logger logger = LogManager.getLogger(Archive.class);

    // Representation of the archive.conf
    private Properties _props;

    // At what interval should we check for archive tasks
    private long _cycleTime;

    // All active ArchiveHandlers
    private Map<UUID, ArchiveHandler> _archiveHandlers;

    // The timer task we created based on _cycleTime
    private TimerTask _runHandler = null;

    // A map of archive types (name -> archiveType class)
    private CaseInsensitiveHashMap<String, Class<? extends ArchiveType>> _typesMap;

    // Get our Properties of archive.conf
    public Properties getProperties() {
        return _props;
    }

    // Get the cycle time
    public long getCycleTime() {
        return _cycleTime;
    }

    // Our Executor Service for running archive handlers
    private ExecutorService _archiveHandlerExecutor;

    /*
     * Returns the archive type corresponding with the .conf file
     * and which Archive number the loop is on
     */
    public ArchiveType getArchiveType(int count, String type, SectionInterface sec, Properties props) {
        ArchiveType archiveType = null;
        Class<?>[] SIG = {Archive.class, SectionInterface.class, Properties.class, int.class};

        if (!_typesMap.containsKey(type)) {
            // if we can't find one filter that will be enought to brake the whole chain.
            logger.error("Archive Type: {} wasn't loaded.", type);

        } else {
            if (!sec.getName().isEmpty()) {
                try {
                    Class<? extends ArchiveType> clazz = _typesMap.get(type);
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
    public CaseInsensitiveHashMap<String, Class<? extends ArchiveType>> getTypesMap() {
        return new CaseInsensitiveHashMap<>(_typesMap);
    }

    /*
     * Load the different Types of Archives specified in plugin.xml
     */
    private void initTypes() {
        CaseInsensitiveHashMap<String, Class<? extends ArchiveType>> typesMap = new CaseInsensitiveHashMap<>();
        Set<Class<? extends ArchiveType>> archiveTypes = new Reflections("org.drftpd").getSubTypesOf(ArchiveType.class);
        for (Class<? extends ArchiveType> archiveType : archiveTypes) {
            typesMap.put(archiveType.getSimpleName(), archiveType);
        }
        _typesMap = typesMap;
    }

    /*
     * Reloads all the different archive's in .conf file
     * Loops though each one and adds to the ArchiveHandler
     */
    private void reload() {
        initTypes();
        _props = ConfigLoader.loadPluginConfig("archive.conf");
        _cycleTime = 60000 * Long.parseLong(PropertyHelper.getProperty(_props, "cycletime", "30").trim());
        int maxConcurrentActions = Integer.parseInt(PropertyHelper.getProperty(_props, "maxConcurrentActions", "10").trim());

        int minActions = getTypesMap().size() + 1;
        if (minActions > maxConcurrentActions) {
            logger.warn("Setting maxConcurrentActions to [" + minActions + "] to allow for your configured archive statements");
            maxConcurrentActions = minActions;
        }
        _archiveHandlerExecutor = Executors.newFixedThreadPool(maxConcurrentActions, new ArchiveHandlerThreadFactory());

        // First cancel and remove our active TimerTask
        if (_runHandler != null) {
            _runHandler.cancel();
            GlobalContext.getGlobalContext().getTimer().purge();
        }

        // Initialize a new TimerTask
        logger.debug("Initializing a new timer task for {} milliseconds", _cycleTime);
        _runHandler = new TimerTask() {
            public void run() {

                logger.debug("TimerTask triggered");
                int count = 1;

                String type;
                while ((type = PropertyHelper.getProperty(_props, count + ".type", null)) != null) {
                    type = type.trim();
                    SectionInterface sec = GlobalContext.getGlobalContext().getSectionManager().getSection(PropertyHelper.getProperty(_props, count + ".section", "").trim());
                    ArchiveType archiveType = getArchiveType(count, type, sec, _props);
                    if (archiveType != null) {
                        logger.debug("config item {} will be executed, type: {}", count, archiveType.toString());
                        executeArchiveType(archiveType);
                    }
                    count++;
                }
                logger.debug("TimeTask finished, if there was any work we started it (touched {} configs)", (count - 1));
            }
        };
        try {
            GlobalContext.getGlobalContext().getTimer().schedule(_runHandler, _cycleTime, _cycleTime);
        } catch (IllegalStateException e) {
            logger.warn("Unable to schedule our TimerTask as the GlobalContext Timer is in an illegal state", e);
        }
    }

    /*
     * Submit a new archive task to be executed
     */
    public void executeArchiveType(ArchiveType at) {

        // Create the Runnable ArchiveHandler
        ArchiveHandler ah = new ArchiveHandler(at);

        // Register it to our active archive handlers and submit it to our executor
        _archiveHandlers.put(ah.getUUID(), ah);
        _archiveHandlerExecutor.submit(ah);
        logger.debug("New archive handler with uuid {} registered and submitted to executor", ah.getUUID());
    }

    /*
     * This Removes archive handler from current archives in use
     */
    public ArchiveHandler removeArchiveHandler(ArchiveHandler handler) {
        logger.debug("Removing archive handler with uuid {} from active handlers", handler.getUUID());
        return _archiveHandlers.remove(handler.getUUID());
    }

    /*
     * Returns all the current ArchiveHandlers
     */
    public Collection<ArchiveHandler> getArchiveHandlers() {
        return Collections.unmodifiableCollection(_archiveHandlers.values());
    }

    /*
     * This checks to see if the current directory is already queued to be archived.
     * throws DuplicateArchiveException if it is.
     */
    public void checkPathForArchiveStatus(String handlerPath) throws DuplicateArchiveException {
        for (ArchiveHandler ah : _archiveHandlers.values()) {
            if(ah.getJobs().size() == 0) {
                // no jobs associated to this archive type yet so it has not been initialized yet
                continue;
            }
            DirectoryHandle dirHandle = ah.getArchiveType().getDirectory();
            if (dirHandle == null) {
                // archiveType is not yet started so directory is not known yet ...
                continue;
            }
            String ahPath = dirHandle.getPath();

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
        logger.info("Received reload event, reloading");
        reload();
    }

    public void startPlugin() {
        // Subscribe to events
        AnnotationProcessor.process(this);
        logger.info("Archive plugin loaded successfully");
        _archiveHandlers = new ConcurrentHashMap<>();
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

    public static class ArchiveHandlerThreadFactory implements ThreadFactory {

        public static String getIdleThreadName(long threadId) {
            return "Archive Handler-" + threadId + " - Waiting for work";
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = Executors.defaultThreadFactory().newThread(r);
            t.setName(getIdleThreadName(t.getId()));
            return t;
        }
    }
}
