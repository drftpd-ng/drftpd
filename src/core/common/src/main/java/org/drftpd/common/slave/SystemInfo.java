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
package org.drftpd.common.slave;

import java.io.File;
import java.io.Serializable;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.DoubleSupplier;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Snapshot of the hardware / system information of a master or a slave, built with JDK APIs only.
 * <p>
 * Every probe is guarded: a value that cannot be read is reported as {@code null} (strings) or a negative
 * number (numbers), it never breaks the whole snapshot.
 * <p>
 * Privacy: this deliberately carries no hostname, no IP address, no user name and no environment variable.
 * The only filesystem information is the list of roots (as configured) with their free and total space.
 */
public class SystemInfo implements Serializable {
    private static final long serialVersionUID = 6157924733624113021L;

    /** Value used for an unknown / unsupported numeric value. */
    public static final long UNKNOWN = -1L;

    /** How long collect() waits between the two CPU samples when this is the first sample of the JVM. */
    private static final long CPU_SAMPLE_PAUSE_MILLIS = 200L;

    /** Set once the CPU load counters of this JVM have been primed. */
    private static final AtomicBoolean CPU_PRIMED = new AtomicBoolean(false);

    private String _osName;
    private String _osVersion;
    private String _osArch;
    private int _availableProcessors = -1;
    private double _systemLoadAverage = -1;
    private double _systemCpuLoad = -1;
    private double _processCpuLoad = -1;
    private long _totalMemory = UNKNOWN;
    private long _freeMemory = UNKNOWN;
    private long _totalSwap = UNKNOWN;
    private long _freeSwap = UNKNOWN;
    private String _javaVersion;
    private String _javaVendor;
    private long _heapUsed = UNKNOWN;
    private long _heapMax = UNKNOWN;
    private long _processUptime = UNKNOWN;
    private String _drftpdVersion;
    private List<RootInfo> _roots = Collections.emptyList();

    /**
     * Free and total space of one root (a slave root, or the master userdata directory).
     */
    public static class RootInfo implements Serializable {
        private static final long serialVersionUID = -2381541720914735146L;

        private final String _path;
        private final long _free;
        private final long _total;
        private final String _fsKey;

        public RootInfo(String path, long free, long total) {
            this(path, free, total, null);
        }

        /**
         * @param fsKey identifies the filesystem holding the root (roots with the same key share the same
         *              space), or null if it could not be determined
         */
        public RootInfo(String path, long free, long total, String fsKey) {
            _path = path;
            _free = free;
            _total = total;
            _fsKey = fsKey;
        }

        /** @return an opaque identifier of the filesystem holding this root, or null if unknown */
        public String getFsKey() {
            return _fsKey;
        }

        public String getPath() {
            return _path;
        }

        /** @return usable bytes, or a negative value if unknown */
        public long getFree() {
            return _free;
        }

        /** @return total bytes, or a negative value if unknown */
        public long getTotal() {
            return _total;
        }

        /** @return used bytes, or a negative value if unknown */
        public long getUsed() {
            if (_free < 0 || _total < 0) {
                return UNKNOWN;
            }
            return Math.max(0L, _total - _free);
        }

        public String toString() {
            return "RootInfo[path=" + _path + ",free=" + _free + ",total=" + _total + "]";
        }
    }

    public SystemInfo() {
    }

    /**
     * Collects the information about the running JVM and its host.
     *
     * @param drftpdVersion the DrFTPD version string of the caller (master or slave)
     * @param rootPaths     the paths whose filesystem free/total space must be reported, in order; may be null
     * @return a snapshot, never null
     */
    public static SystemInfo collect(String drftpdVersion, Collection<String> rootPaths) {
        SystemInfo si = new SystemInfo();
        si._drftpdVersion = drftpdVersion;

        OperatingSystemMXBean os = probe(ManagementFactory::getOperatingSystemMXBean, null);
        if (os != null) {
            si._osName = probe(os::getName, null);
            si._osVersion = probe(os::getVersion, null);
            si._osArch = probe(os::getArch, null);
            si._availableProcessors = (int) probeLong(os::getAvailableProcessors);
            si._systemLoadAverage = probeDouble(os::getSystemLoadAverage);

            if (os instanceof com.sun.management.OperatingSystemMXBean) {
                com.sun.management.OperatingSystemMXBean sun = (com.sun.management.OperatingSystemMXBean) os;
                // The CPU load is computed between two samples: the very first call in a JVM has no previous
                // sample and reports 0, so take a throw away sample and wait a little before the real one.
                primeOnce(CPU_PRIMED, () -> sampleCpu(sun), SystemInfo::pauseBetweenSamples);
                si._systemCpuLoad = probeDouble(sun::getCpuLoad);
                si._processCpuLoad = probeDouble(sun::getProcessCpuLoad);
                si._totalMemory = probeLong(sun::getTotalMemorySize);
                si._freeMemory = probeLong(sun::getFreeMemorySize);
                // On Linux getFreeMemorySize() is MemFree, which excludes the page cache and makes a busy
                // machine look full: prefer MemAvailable when /proc/meminfo has it.
                long available = probeLong(SystemInfo::readMemAvailable);
                if (available >= 0) {
                    si._freeMemory = available;
                }
                si._totalSwap = probeLong(sun::getTotalSwapSpaceSize);
                si._freeSwap = probeLong(sun::getFreeSwapSpaceSize);
            }
        }

        si._javaVersion = probe(() -> System.getProperty("java.version"), null);
        si._javaVendor = probe(() -> System.getProperty("java.vendor"), null);

        MemoryUsage heap = probe(() -> ManagementFactory.getMemoryMXBean().getHeapMemoryUsage(), null);
        if (heap != null) {
            si._heapUsed = heap.getUsed();
            si._heapMax = heap.getMax();
        }
        si._processUptime = probeLong(() -> ManagementFactory.getRuntimeMXBean().getUptime());

        List<RootInfo> roots = new ArrayList<>();
        if (rootPaths != null) {
            for (String path : rootPaths) {
                if (path == null) {
                    continue;
                }
                File f = new File(path);
                long total = probeLong(f::getTotalSpace);
                long free = probeLong(f::getUsableSpace);
                // File.getTotalSpace() returns 0 for a path that does not exist: report it as unknown
                if (total <= 0) {
                    total = UNKNOWN;
                    free = UNKNOWN;
                }
                roots.add(new RootInfo(path, free, total, total < 0 ? null : filesystemKey(f)));
            }
        }
        si._roots = Collections.unmodifiableList(roots);
        return si;
    }

    /**
     * Primes the CPU load counters of the JVM (one throw away sample), so that the first real measurement
     * does not report 0. Meant to be called once at master and slave startup; harmless to call again.
     */
    public static void primeCpuLoad() {
        OperatingSystemMXBean os = probe(ManagementFactory::getOperatingSystemMXBean, null);
        if (os instanceof com.sun.management.OperatingSystemMXBean) {
            com.sun.management.OperatingSystemMXBean sun = (com.sun.management.OperatingSystemMXBean) os;
            primeOnce(CPU_PRIMED, () -> sampleCpu(sun), null);
        }
    }

    /**
     * Runs {@code sampler} and then {@code pause} the first time it is called with this flag, does nothing
     * afterwards.
     *
     * @param pause optional, may be null
     * @return true if the sampling was done by this call
     */
    static boolean primeOnce(AtomicBoolean primed, Runnable sampler, Runnable pause) {
        if (!primed.compareAndSet(false, true)) {
            return false;
        }
        try {
            sampler.run();
        } catch (Throwable t) {
            // unsupported platform, the values will simply be reported as unknown
        }
        if (pause != null) {
            pause.run();
        }
        return true;
    }

    private static void sampleCpu(com.sun.management.OperatingSystemMXBean sun) {
        sun.getCpuLoad();
        sun.getProcessCpuLoad();
    }

    private static void pauseBetweenSamples() {
        try {
            Thread.sleep(CPU_SAMPLE_PAUSE_MILLIS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Builds a key identifying the filesystem that holds {@code f}, or null if it cannot be determined.
     * <p>
     * On Unix the device number of the path ({@code unix:dev}) is used: it is exact, and it also tells apart
     * two mounts that happen to have the same name or size. On Windows the volume serial number of the
     * {@link FileStore} is used when the JVM exposes it. Otherwise the key falls back to the file store name,
     * type and total size, which can only wrongly merge two distinct filesystems with identical name, type
     * and size (e.g. two unnamed same-size volumes), never split one filesystem in two. A filesystem
     * mounted twice (bind mount) is seen as one device on Unix, which is what we want here.
     */
    static String filesystemKey(File f) {
        try {
            Path p = f.toPath();
            try {
                Object dev = Files.getAttribute(p, "unix:dev");
                if (dev != null) {
                    return "dev:" + dev;
                }
            } catch (Throwable t) {
                // not a Unix filesystem view
            }
            FileStore store = Files.getFileStore(p);
            try {
                Object vsn = store.getAttribute("volume:vsn");
                if (vsn != null) {
                    return "vsn:" + vsn;
                }
            } catch (Throwable t) {
                // attribute not supported
            }
            return "fs:" + store.name() + "|" + store.type() + "|" + store.getTotalSpace();
        } catch (Throwable t) {
            return null;
        }
    }

    private static <T> T probe(Supplier<T> s, T fallback) {
        try {
            T value = s.get();
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
    }

    /** @return MemAvailable of /proc/meminfo in bytes, or UNKNOWN when not on Linux or not readable */
    private static long readMemAvailable() {
        Path meminfo = Path.of("/proc/meminfo");
        if (!Files.isReadable(meminfo)) {
            return UNKNOWN;
        }
        try {
            return parseMemAvailable(Files.readAllLines(meminfo));
        } catch (Exception e) {
            return UNKNOWN;
        }
    }

    /**
     * Extracts MemAvailable from the lines of /proc/meminfo.
     *
     * @return the value in bytes, or UNKNOWN if the line is missing or malformed
     */
    static long parseMemAvailable(List<String> lines) {
        for (String line : lines) {
            if (!line.startsWith("MemAvailable:")) {
                continue;
            }
            String[] parts = line.substring("MemAvailable:".length()).trim().split("\\s+");
            try {
                long value = Long.parseLong(parts[0]);
                if (value < 0) {
                    return UNKNOWN;
                }
                if (parts.length > 1 && !parts[1].equalsIgnoreCase("kB")) {
                    return UNKNOWN;
                }
                return value * 1024L;
            } catch (NumberFormatException e) {
                return UNKNOWN;
            }
        }
        return UNKNOWN;
    }

    private static long probeLong(LongSupplier s) {
        try {
            long value = s.getAsLong();
            return value < 0 ? UNKNOWN : value;
        } catch (Throwable t) {
            return UNKNOWN;
        }
    }

    private static double probeDouble(DoubleSupplier s) {
        try {
            double value = s.getAsDouble();
            return (Double.isNaN(value) || value < 0) ? -1 : value;
        } catch (Throwable t) {
            return -1;
        }
    }

    public String getOsName() {
        return _osName;
    }

    public String getOsVersion() {
        return _osVersion;
    }

    public String getOsArch() {
        return _osArch;
    }

    /** @return number of processors available to the JVM, or a negative value if unknown */
    public int getAvailableProcessors() {
        return _availableProcessors;
    }

    /** @return system load average of the last minute, or a negative value if unavailable (e.g. on Windows) */
    public double getSystemLoadAverage() {
        return _systemLoadAverage;
    }

    /** @return system CPU load in [0.0, 1.0], or a negative value if unavailable */
    public double getSystemCpuLoad() {
        return _systemCpuLoad;
    }

    /** @return CPU load of this JVM process in [0.0, 1.0], or a negative value if unavailable */
    public double getProcessCpuLoad() {
        return _processCpuLoad;
    }

    public long getTotalMemory() {
        return _totalMemory;
    }

    public long getFreeMemory() {
        return _freeMemory;
    }

    /** @return used physical memory, or a negative value if unknown */
    public long getUsedMemory() {
        return used(_totalMemory, _freeMemory);
    }

    public long getTotalSwap() {
        return _totalSwap;
    }

    public long getFreeSwap() {
        return _freeSwap;
    }

    /** @return used swap, or a negative value if unknown */
    public long getUsedSwap() {
        return used(_totalSwap, _freeSwap);
    }

    public String getJavaVersion() {
        return _javaVersion;
    }

    public String getJavaVendor() {
        return _javaVendor;
    }

    public long getHeapUsed() {
        return _heapUsed;
    }

    /** @return maximum heap size, or a negative value if undefined */
    public long getHeapMax() {
        return _heapMax;
    }

    /** @return uptime of the JVM process in milliseconds, or a negative value if unknown */
    public long getProcessUptime() {
        return _processUptime;
    }

    public String getDrftpdVersion() {
        return _drftpdVersion;
    }

    public List<RootInfo> getRoots() {
        return _roots == null ? Collections.emptyList() : _roots;
    }

    /**
     * @return the roots with a known capacity, one per distinct filesystem (the first root seen for each);
     * roots whose filesystem could not be identified are all kept
     */
    private List<RootInfo> distinctFilesystems() {
        List<RootInfo> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (RootInfo r : getRoots()) {
            if (r.getTotal() < 0) {
                continue;
            }
            if (r.getFsKey() == null || seen.add(r.getFsKey())) {
                list.add(r);
            }
        }
        return list;
    }

    /** @return number of distinct filesystems among the roots with a known capacity */
    public int getFilesystemCount() {
        return distinctFilesystems().size();
    }

    /** @return sum of the capacities of the distinct filesystems, or a negative value if none is known */
    public long getDisksTotal() {
        long sum = UNKNOWN;
        for (RootInfo r : distinctFilesystems()) {
            sum = Math.max(sum, 0L) + r.getTotal();
        }
        return sum;
    }

    /** @return sum of the usable space of the distinct filesystems, or a negative value if none is known */
    public long getDisksFree() {
        long sum = UNKNOWN;
        for (RootInfo r : distinctFilesystems()) {
            if (r.getFree() >= 0) {
                sum = Math.max(sum, 0L) + r.getFree();
            }
        }
        return sum;
    }

    /** @return used space over the distinct filesystems, or a negative value if none is known */
    public long getDisksUsed() {
        return used(getDisksTotal(), getDisksFree());
    }

    private static long used(long total, long free) {
        if (total < 0 || free < 0) {
            return UNKNOWN;
        }
        return Math.max(0L, total - free);
    }

    public String toString() {
        return "SystemInfo[os=" + _osName + " " + _osVersion + " (" + _osArch + "),cores=" + _availableProcessors
                + ",cpu=" + _systemCpuLoad + ",mem=" + _freeMemory + "/" + _totalMemory
                + ",java=" + _javaVersion + ",version=" + _drftpdVersion + ",roots=" + getRoots() + "]";
    }
}
