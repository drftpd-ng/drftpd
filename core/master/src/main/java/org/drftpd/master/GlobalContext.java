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
package org.drftpd.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.EventServiceExistsException;
import org.bushe.swing.event.EventServiceLocator;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.common.CommandHook;
import org.drftpd.common.PluginDependencies;
import org.drftpd.master.commandmanager.CommandManagerInterface;
import org.drftpd.master.common.PropertyHelper;
import org.drftpd.master.common.util.PortRange;
import org.drftpd.master.config.ConfigManager;
import org.drftpd.master.event.AsyncThreadSafeEventService;
import org.drftpd.master.event.MessageEvent;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.exceptions.SlaveFileException;
import org.drftpd.master.master.CommitManager;
import org.drftpd.master.master.ConnectionManager;
import org.drftpd.master.master.SlaveManager;
import org.drftpd.master.master.config.ConfigInterface;
import org.drftpd.master.master.config.PluginsConfig;
import org.drftpd.master.master.cron.TimeEventInterface;
import org.drftpd.master.master.cron.TimeManager;
import org.drftpd.master.sections.SectionManagerInterface;
import org.drftpd.master.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.master.usermanager.AbstractUserManager;
import org.drftpd.master.usermanager.UserManager;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.master.vfs.index.IndexEngineInterface;
import org.drftpd.slave.SSLGetContext;
import org.reflections.Reflections;
import org.reflections.scanners.MethodAnnotationsScanner;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;

import javax.net.ssl.SSLContext;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */

public class GlobalContext {

    private static final Logger logger = LogManager.getLogger(GlobalContext.class);

    protected static GlobalContext _gctx;

    private PluginsConfig _pluginsConfig;

    private ConfigInterface _config;

    private List<PluginInterface> _plugins = new ArrayList<>();

    protected SectionManagerInterface _sectionManager;

    private String _shutdownMessage = null;

    protected SlaveManager _slaveManager;

    protected AbstractUserManager _usermanager;

    private Timer _timer = new Timer("GlobalContextTimer");

    protected SlaveSelectionManagerInterface _slaveSelectionManager;

    private SSLContext _sslContext;

    private TimeManager _timeManager;

    private IndexEngineInterface _indexEngine;

    private static DirectoryHandle root = new DirectoryHandle(VirtualFileSystem.separator);

    private static AsyncThreadSafeEventService eventService = new AsyncThreadSafeEventService();

    private static Set<Method> hooksMethods;

    public static final String VERSION = "DrFTPD v4";

    public void reloadFtpConfig() {
        _config.reload();
    }

    /**
     * If you're creating a GlobalContext object and it's not part of a TestCase
     * you're not doing it correctly, GlobalContext is a Singleton
     */
    protected GlobalContext() {
        Reflections reflections = new Reflections(new ConfigurationBuilder()
                .setUrls(ClasspathHelper.forPackage("org.drftpd"))
                .setScanners(new MethodAnnotationsScanner()));

        hooksMethods = reflections.getMethodsAnnotatedWith(CommandHook.class);
    }

    public static Set<Method> getHooksMethods() {
        return hooksMethods;
    }

    private void loadSlaveSelectionManager(Properties cfg) {
        String desiredSL = PropertyHelper.getProperty(cfg, "slaveselection");
        try {
            Class<?> aClass = Class.forName(desiredSL);
            _slaveSelectionManager = (SlaveSelectionManagerInterface) aClass.getConstructor().newInstance();
            // TODO [DONE] @JRI slave selector
        } catch (Exception e) {
            throw new FatalException("Unable to load the slaveselection plugin, check config.", e);
        }
    }

    public PluginsConfig getPluginsConfig() {
        return _pluginsConfig;
    }

    public void loadPluginsConfig() {
        _pluginsConfig = new PluginsConfig();
    }

    public static ConnectionManager getConnectionManager() {
        return ConnectionManager.getConnectionManager();
    }

    public static ConfigInterface getConfig() {
        return getGlobalContext()._config;
    }

    public List<PluginInterface> getPlugins() {
        return new ArrayList<>(_plugins);
    }

    public SectionManagerInterface getSectionManager() {
        if (_sectionManager == null) {
            throw new NullPointerException();
        }

        return _sectionManager;
    }

    public String getShutdownMessage() {
        return _shutdownMessage;
    }

    public SlaveManager getSlaveManager() {
        if (_slaveManager == null) {
            throw new NullPointerException();
        }

        return _slaveManager;
    }

    public IndexEngineInterface getIndexEngine() {
        return _indexEngine;
    }

    public UserManager getUserManager() {
        if (_usermanager == null) {
            throw new NullPointerException();
        }

        return _usermanager;
    }

    public boolean isShutdown() {
        return _shutdownMessage != null;
    }

    public CommandManagerInterface createCommandManager() {
        Properties cfg = GlobalContext.getConfig().getMainProperties();
        String desiredCm = PropertyHelper.getProperty(cfg, "commandmanager");
        try {
            Class<?> aClass = Class.forName(desiredCm);
            return (CommandManagerInterface) aClass.getConstructor().newInstance();
            // TODO [DONE] @JRI Command Manager
        } catch (Exception e) {
            throw new FatalException(
                    "Cannot create instance of commandmanager, check 'commandmanager' in the configuration file",
                    e);
        }
    }

    private void loadPlugins() {
        // TODO [DONE] @JRI Load plugins
        Set<Class<? extends PluginInterface>> plugins = new Reflections("org.drftpd").getSubTypesOf(PluginInterface.class);
        List<String> alreadyResolved = new ArrayList<>();
        try {
            boolean allResolve = false;
            while(!allResolve) {
                for (Class<? extends PluginInterface> plugin : plugins) {
                    PluginDependencies annotation = plugin.getAnnotation(PluginDependencies.class);
                    List<Class<? extends PluginInterface>> dependencies = annotation != null ?
                            Arrays.asList(annotation.refs()) : new ArrayList<>();
                    List<String> depNames = dependencies.stream().map(Class::getName).collect(Collectors.toList());
                    boolean alreadyInstantiate = alreadyResolved.contains(plugin.getName());
                    if (alreadyResolved.containsAll(depNames) && !alreadyInstantiate) {
                        PluginInterface pluginInterface = plugin.getConstructor().newInstance();
                        pluginInterface.startPlugin();
                        _plugins.add(pluginInterface);
                        alreadyResolved.add(plugin.getName());
                    }
                    if (plugins.size() == _plugins.size()) {
                        allResolve = true;
                    }
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for master extension point 'Plugin', possibly the master extension " +
                    "point definition has changed in the plugin.xml", e);
        }
    }

    private void loadSectionManager(Properties cfg) {
        String desiredSm = PropertyHelper.getProperty(cfg, "sectionmanager");
        try {
            Class<?> aClass = Class.forName(desiredSm);
            _sectionManager = (SectionManagerInterface) aClass.getConstructor().newInstance();
            // TODO [DONE] @JRI Section Manager
        } catch (Exception e) {
            throw new FatalException("Cannot create instance of SectionManager, check 'sectionmanager' in config file", e);
        }
    }

    private void loadIndexingEngine(Properties cfg) {
        String desiredIe = PropertyHelper.getProperty(cfg, "indexingengine");
        try {
            Class<?> aClass = Class.forName(desiredIe);
            _indexEngine = (IndexEngineInterface) aClass.getConstructor().newInstance();
            _indexEngine.init();
            // TODO [DONE]  @JRI Plug index engine
        } catch (Exception e) {
            throw new FatalException("Cannot create instance of IndexingEngine, check 'indexingengine' in config file", e);
        }
    }

    /**
     * Depends on root loaded if any slaves connect early.
     */
    private void loadSlaveManager(Properties cfg) throws SlaveFileException {
        /** register slavemanager * */
        _slaveManager = new SlaveManager(cfg);
    }

    private void listenForSlaves() {
        new Thread(_slaveManager, "Listening for slave connections - "
                + _slaveManager.toString()).start();
    }

    protected void loadUserManager(Properties cfg) {
        String desiredUm = PropertyHelper.getProperty(cfg, "usermanager");
        try {
            Class<?> aClass = Class.forName(desiredUm);
            _usermanager = (AbstractUserManager) aClass.getConstructor().newInstance();
            _usermanager.init();
        } catch (Exception e) {
            throw new FatalException(
                    "Cannot create instance of usermanager, check 'usermanager' in the configuration file",
                    e);
        }
    }

    /**
     * Doesn't close connections like ConnectionManager.close() does
     * ConnectionManager.close() calls this method.
     */
    public void shutdown(String message) {
        _shutdownMessage = message;
        CommitManager.getCommitManager().enableQueueDrain();
        getEventService().publish(new MessageEvent("SHUTDOWN", message));
        getConnectionManager().shutdownPrivate(message);
        new Thread(new Shutdown()).start();
    }

    static class Shutdown implements Runnable {

        public void run() {
            Thread.currentThread().setName("Shutdown Thread");
            while (GlobalContext.getConnectionManager().getConnections().size() > 0) {
                logger.info("Waiting for connections to be shutdown...");
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
            while (GlobalContext.getEventService().getQueueSize() > 0) {
                logger.info("Waiting for queued events to be processed - {} remaining", GlobalContext.getEventService().getQueueSize());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
            while (CommitManager.getCommitManager().getQueueSize() > 0) {
                logger.info("Waiting for queued commits to be drained - {} remaining", CommitManager.getCommitManager().getQueueSize());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                }
            }
            logger.info("Shutdown complete, exiting");
            System.exit(0);
        }
    }

    public Timer getTimer() {
        return _timer;
    }

    public SlaveSelectionManagerInterface getSlaveSelectionManager() {
        return _slaveSelectionManager;
    }

    public void addTimeEvent(TimeEventInterface timeEvent) {
        _timeManager.addTimeEvent(timeEvent);
    }

    public void removeTimeEvent(TimeEventInterface timeEvent) {
        _timeManager.removeTimeEvent(timeEvent);
    }

    public PortRange getPortRange() {
        return getConfig().getPortRange();
    }

    public static GlobalContext getGlobalContext() {
        if (_gctx == null) {
            _gctx = new GlobalContext();
            try {
                EventServiceLocator.setEventService(EventServiceLocator.SERVICE_NAME_EVENT_BUS, eventService);
            } catch (EventServiceExistsException e) {
                logger.error("Error setting event service, likely something using the event bus before GlobalContext is instantiated", e);
            }
        }
        return _gctx;
    }

    public DirectoryHandle getRoot() {
        return root;
    }

    public void init() {
        _config = new ConfigManager();
        _config.reload();

        CommitManager.getCommitManager().start();
        _timeManager = new TimeManager();
        loadPluginsConfig();
        loadUserManager(getConfig().getMainProperties());
        addTimeEvent(getUserManager());

        try {
            _sslContext = SSLGetContext.getSSLContext();
        } catch (IOException e) {
            logger.warn("Couldn't load SSLContext, SSL/TLS disabled - {}", e.getMessage());
        } catch (Exception e) {
            logger.warn("Couldn't load SSLContext, SSL/TLS disabled", e);
        }

        try {
            loadSlaveManager(getConfig().getMainProperties());
        } catch (SlaveFileException e) {
            throw new RuntimeException(e);
        }
        listenForSlaves();
        loadSlaveSelectionManager(getConfig().getMainProperties());
        loadSectionManager(getConfig().getMainProperties());
        loadIndexingEngine(getConfig().getMainProperties());
        loadPlugins();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    /**
     * Will return null if SSL/TLS is not configured
     */
    public SSLContext getSSLContext() {
        return _sslContext;
    }

    public static HashMap<String, Properties> loadCommandConfig(String confDirectory) {
        HashMap<String, Properties> commandsConfig = new HashMap<>();
        LineNumberReader reader = null;
        try {
            Stream<Path> pathStream = Files.walk(Paths.get(confDirectory));
            List<Path> confFiles = pathStream.filter(f -> f.getFileName().toString().endsWith(".conf")).collect(Collectors.toList());
            for (Path confFile : confFiles) {
                reader = new LineNumberReader(new FileReader(confFile.toFile()));
                String curLine;

                while (reader.ready()) {
                    curLine = reader.readLine();
                    if (curLine != null) {
                        curLine = curLine.trim();
                        if (curLine.startsWith("#") || curLine.equals("") || curLine.startsWith("skip")) {
                            // comment or blank line, ignore
                            continue;
                        }
                        if (curLine.endsWith("{")) {
                            // internal loop
                            String cmdName = curLine.substring(0, curLine.lastIndexOf("{") - 1).toLowerCase();
                            if (commandsConfig.containsKey(cmdName)) {
                                throw new FatalException(cmdName + " is already mapped on line " + reader.getLineNumber());
                            }
                            Properties p = getPropertiesUntilClosed(reader);
                            logger.trace("Adding command {}", cmdName);

                            commandsConfig.put(cmdName, p);
                        } else {
                            throw new FatalException("Expected line to end with \"{\" at line " + reader.getLineNumber());
                        }
                    }
                }
            }
            // done reading for new commands, must be finished
        } catch (IOException e) {
            throw new FatalException("Error loading " + confDirectory, e);
        } catch (Exception e) {
            if (reader != null) {
                logger.error("Error reading line {} in {}", reader.getLineNumber(), confDirectory);
            }
            throw new FatalException(e);
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                }
            }
        }
        return commandsConfig;
    }

    private static Properties getPropertiesUntilClosed(LineNumberReader reader) throws IOException {
        Properties p = new Properties();
        String curLine = null;
        while (reader.ready()) {
            curLine = reader.readLine();
            if (curLine != null) {
                curLine = curLine.trim();
                if (curLine.startsWith("#") || curLine.equals("")) {
                    // comment or blank line, ignore
                    continue;
                }
                if (curLine.equals("}")) {
                    // end of this block
                    return p;
                }
                // internal loop
                int spaceIndex = curLine.indexOf(" ");
                if (spaceIndex == -1) {
                    throw new FatalException("Line " + reader.getLineNumber() + " is not formatted properly");
                }
                String propName = curLine.substring(0, spaceIndex);
                String value = curLine.substring(spaceIndex).trim();
                String concatenate = p.getProperty(propName);
                if (concatenate == null) {
                    p.put(propName, value);
                } else {
                    p.put(propName, concatenate + "\n" + value);
                }
            }
        }
        throw new FatalException("Premature end of file, not enough \"}\" characters exist.");
    }

    public static AsyncThreadSafeEventService getEventService() {
        return eventService;
    }

	/*
	@EventSubscriber
	public synchronized void onUnloadPluginEvent(Object event) {

	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(Object event) {

	}
	*/
}
