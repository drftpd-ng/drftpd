package org.drftpd.autofreespace.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.*;
import java.util.function.Predicate;

public class AutoFreeSpaceSettings {
    private static final Logger logger = LogManager.getLogger(AutoFreeSpaceSettings.class);
    public static String MODE_DISABLED = "Disabled";
    public static String MODE_DATE = "Date";
    public static String MODE_SPACE = "Space";

    static final String PATH_KEY_PREFIX = "path:";
    private static AutoFreeSpaceSettings ref;
    private Map<String, Section> _sections;
    private List<String> _excludeFiles;
    private List<String> _excludeSlaves;
    private boolean _onlyAnnounce;
    private String _mode;
    private long _minFreeSpace;
    private long _cycleTime;
    private int _maxIterations;
    private boolean _wipeAllSections;

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

        _sections = parseEntries(p, _mode,
                name -> GlobalContext.getGlobalContext().getSectionManager().getSection(name).getName().equalsIgnoreCase(name));

        // Wiping every section is opt-in, and only applies when no entry is written at all:
        // a skipped entry (e.g. a typo in a section name) never widens it to every section
        _wipeAllSections = useAllSections(_mode, p.getProperty("wipe.all.sections", "false").equalsIgnoreCase("true"), countEntries(p));
        if (_mode.equals(MODE_SPACE) && _sections.isEmpty() && !_wipeAllSections) {
            logger.warn("No valid X.section/X.path entry configured for AutoFreeSpace space mode, nothing will be deleted");
        }

        ArrayList<String> excludeFiles = new ArrayList<>();
        // Handle excludeFiles
        for (int i = 1; ; i++) {
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

    public int getMaxIterations() { return _maxIterations; }

    public boolean getWipeAllSections() {
        return _wipeAllSections;
    }

    /**
     * Parses the entries: each id has either {@code X.section=<section>} or {@code X.path=<path>}.
     * An entry naming a section for which {@code sectionExists} is false is skipped.
     */
    static Map<String, Section> parseEntries(Properties p, String mode, Predicate<String> sectionExists) {
        Map<String, Section> sections = new HashMap<>();
        for (int id = 1; ; id++) {
            String name = PropertyHelper.getProperty(p, id + ".section", null);
            String path = PropertyHelper.getProperty(p, id + ".path", null);
            if (name == null && path == null) {
                break;
            }
            Long wipeAfter = parseWipeAfter(p, id, mode);
            if (wipeAfter == null) {
                logger.error("Entry [{}] has no valid {}.wipeAfter, which is required in date mode, not creating configuration items", id, id);
            } else if (path != null && !isUsablePath(path)) {
                logger.error("Entry [{}] has {}.path=[{}], which is the root directory or contains '.' or '..', not creating configuration items", id, id, path);
            } else if (path != null) {
                if (name != null) {
                    logger.warn("Entry [{}] has both {}.section and {}.path, using the path", id, id, id);
                }
                sections.put(PATH_KEY_PREFIX + path, new Section(id, null, path, wipeAfter));
                logger.debug("Loaded path {}, wipeAfter: {}", path, wipeAfter);
            } else if (!sectionExists.test(name)) {
                logger.warn("Section [{}] Does not exist, not creating configuration items", name);
            } else {
                sections.put(name, new Section(id, name, null, wipeAfter));
                logger.debug("Loaded section {}, wipeAfter: {}", name, wipeAfter);
            }
        }
        return sections;
    }

    /**
     * A path entry must name at least one directory below the root, and may not use {@code .} or {@code ..}:
     * with the root itself, every top level directory (whole sections, PRE, group dirs) would become a release.
     */
    static boolean isUsablePath(String path) {
        boolean hasPart = false;
        for (String part : path.trim().split("/")) {
            if (part.equals(".") || part.equals("..")) {
                return false;
            }
            if (!part.isEmpty()) {
                hasPart = true;
            }
        }
        return hasPart;
    }

    /** Returns how many entries are written in the config, valid or not. */
    static int countEntries(Properties p) {
        int id = 1;
        while (p.getProperty(id + ".section") != null || p.getProperty(id + ".path") != null) {
            id++;
        }
        return id - 1;
    }

    /**
     * Every section is considered only in space mode, with {@code wipe.all.sections=true}
     * and no entry written at all (a skipped entry, such as a mistyped section name, still counts).
     */
    static boolean useAllSections(String mode, boolean wipeAllSections, int entryCount) {
        return MODE_SPACE.equals(mode) && wipeAllSections && entryCount == 0;
    }

    /**
     * Reads {@code <id>.wipeAfter} (in minutes) and returns it in milliseconds.
     * The value is required in date mode, where a missing value must never mean "delete everything":
     * a missing or invalid value returns {@code null} so the entry is skipped.
     * In the other modes wipeAfter is not used, so a missing or invalid value returns 0.
     */
    static Long parseWipeAfter(Properties p, int id, String mode) {
        String value = p.getProperty(id + ".wipeAfter");
        long minutes;
        try {
            minutes = Long.parseLong(value == null ? "" : value.trim());
        } catch (NumberFormatException e) {
            return MODE_DATE.equals(mode) ? null : 0L;
        }
        if (minutes < 0) {
            return MODE_DATE.equals(mode) ? null : 0L;
        }
        return minutes * 60000L;
    }

    static class Section {
        private final int id;
        private final String name;
        private final String path;
        private final long wipeAfter;

        /**
         * @param name section name, or {@code null} for a path entry
         * @param path VFS path for a path entry, or {@code null} for a section entry.
         *             A {@code *} component matches every subdirectory at that level, e.g. {@code /MP3/*}.
         */
        public Section(int id, String name, String path, long wipeAfter) {
            this.id = id;
            this.name = name;
            this.path = path;
            this.wipeAfter = wipeAfter;
        }

        public String getPath() {
            return this.path;
        }

        public int getId() {
            return this.id;
        }

        public String getName() {
            return this.name;
        }

        public long getWipeAfter() {
            return this.wipeAfter;
        }
    }
}

