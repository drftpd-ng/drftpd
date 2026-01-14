package org.drftpd.autofreespace.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.*;

public class AutoFreeSpaceSettings {
    private static final Logger logger = LogManager.getLogger(AutoFreeSpaceSettings.class);
    public static String MODE_DISABLED = "Disabled";
    public static String MODE_DATE = "Date";
    public static String MODE_SPACE = "Space";
    private static AutoFreeSpaceSettings ref;
    private Map<String, Section> _sections;
    private List<String> _excludeFiles;
    private List<String> _excludeSlaves;
    private boolean _onlyAnnounce;
    private String _mode;
    private long _minFreeSpace;
    private long _cycleTime;
    private int _maxIterations;

    private AutoFreeSpaceSettings() {
        // Set defaults (just in case)
        _sections = new HashMap<>();
        _excludeFiles = new ArrayList<>();
        _excludeSlaves = new ArrayList<>();
        _onlyAnnounce = true;
        _mode = MODE_DISABLED;
        _minFreeSpace = 0L;
        _cycleTime = 10080L * 60000L;
        _maxIterations = 5;
        reload();
    }

    public static synchronized AutoFreeSpaceSettings getSettings() {
        if (ref == null) {
            // it's ok, we can call this constructor
            ref = new AutoFreeSpaceSettings();
        }
        return ref;
    }

    public void reload() {
        logger.debug("Loading configuration");
        Properties p = ConfigLoader.loadPluginConfig("autofreespace.conf");

        // Quickly set the ones that are single:
        _onlyAnnounce = p.getProperty("announce.only", "false").equalsIgnoreCase("true");
        _minFreeSpace = Bytes.parseBytes(p.getProperty("keepFree"));
        _cycleTime = Long.parseLong(p.getProperty("cycleTime")) * 60000L;
        _maxIterations = Integer.parseInt(p.getProperty("max.iterations"));

        // Handle operating mode
        String mode = p.getProperty("mode", MODE_DISABLED);
        if (mode.equalsIgnoreCase(MODE_SPACE)) {
            _mode = MODE_SPACE;
        } else if (mode.equalsIgnoreCase(MODE_DATE)) {
            _mode = MODE_DATE;
        } else {
            if (!mode.equalsIgnoreCase(MODE_DISABLED)) {
                logger.error("Incorrect mode [{}] detected for AutoFreeSpace, plugin disabled!!!", mode);
            }
            _mode = MODE_DISABLED;
        }

        List<String> excludeSlaves = new ArrayList<>();

        // Handle excludeSlaves
        if (p.getProperty("excluded.slaves") != null) {
            for (String slaveName : p.getProperty("excluded.slaves").trim().split("\\s")) {
                try {
                    GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
                    excludeSlaves.add(slaveName);
                } catch (ObjectNotFoundException e) {
                    logger.error("Slave with name [{}] does not exist, config error", slaveName, e);
                }
            }
        }

        _excludeSlaves = excludeSlaves;
        logger.debug("excluded Slaves set to {}", _excludeSlaves.toString());

        Map<String, Section> sections = new HashMap<>();
        int id = 1;

        // Handle sections - supports both X.section=<name> and X.path=<pattern>
        while (true) {
            String sectionName = PropertyHelper.getProperty(p, id + ".section", null);
            String pathPattern = PropertyHelper.getProperty(p, id + ".path", null);

            // Stop if neither section nor path is configured for this id
            if (sectionName == null && pathPattern == null) {
                break;
            }

            long wipeAfter = Long.parseLong(p.getProperty(id + ".wipeAfter", "0")) * 60000L;

            if (pathPattern != null) {
                // Path-based configuration: use path pattern directly
                String key = "path:" + id;
                sections.put(key, new Section(id, null, pathPattern, wipeAfter));
                logger.debug("Loaded path pattern [{}], wipeAfter: {}", pathPattern, wipeAfter);
            } else if (sectionName != null) {
                // Section-based configuration: validate section exists
                if (!GlobalContext.getGlobalContext().getSectionManager().getSection(sectionName).getName()
                        .equalsIgnoreCase(sectionName)) {
                    logger.error("Section [{}] does not exist, not creating configuration items", sectionName);
                } else {
                    sections.put(sectionName, new Section(id, sectionName, null, wipeAfter));
                    logger.debug("Loaded section {}, wipeAfter: {}", sectionName, wipeAfter);
                }
            }
            id++;
        }
        _sections = sections;

        ArrayList<String> excludeFiles = new ArrayList<>();
        // Handle excludeFiles
        for (int i = 1;; i++) {
            String sec = p.getProperty("excluded.file." + i);
            if (sec == null)
                break;
            excludeFiles.add(sec);
        }
        excludeFiles.trimToSize();
        _excludeFiles = excludeFiles;
        logger.debug("excluded Files set to {}", _excludeFiles.toString());
    }

    public Map<String, Section> getSections() {
        return _sections;
    }

    public List<String> getExcludeFiles() {
        return _excludeFiles;
    }

    public List<String> getExcludeSlaves() {
        return _excludeSlaves;
    }

    public boolean getOnlyAnnounce() {
        return _onlyAnnounce;
    }

    public String getMode() {
        return _mode;
    }

    public long getMinFreeSpace() {
        return _minFreeSpace;
    }

    public long getCycleTime() {
        return _cycleTime;
    }

    public int getMaxIterations() {
        return _maxIterations;
    }

    static class Section {
        private final int id;
        private final String name;
        private final String path;
        private final long wipeAfter;

        public Section(int id, String name, String path, long wipeAfter) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.wipeAfter = wipeAfter;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        /**
         * Returns the path pattern for this section.
         * If null, use the section's base directory.
         * Supports glob patterns like {@code /MP3/*
         /
    }

    with wildcards*/

    public String getPath() {
        return this.path;
    }

    public long getWipeAfter() {
        return this.wipeAfter;
    }
}}
