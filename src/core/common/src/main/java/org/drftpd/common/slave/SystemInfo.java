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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
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

        public RootInfo(String path, long free, long total) {
            _path = path;
            _free = free;
            _total = total;
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
                si._systemCpuLoad = probeDouble(sun::getCpuLoad);
                si._processCpuLoad = probeDouble(sun::getProcessCpuLoad);
                si._totalMemory = probeLong(sun::getTotalMemorySize);
                si._freeMemory = probeLong(sun::getFreeMemorySize);
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
                roots.add(new RootInfo(path, free, total));
            }
        }
        si._roots = Collections.unmodifiableList(roots);
        return si;
    }

    private static <T> T probe(Supplier<T> s, T fallback) {
        try {
            T value = s.get();
            return value == null ? fallback : value;
        } catch (Throwable t) {
            return fallback;
        }
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

    /** @return sum of the known root capacities, or a negative value if none is known */
    public long getDisksTotal() {
        long sum = UNKNOWN;
        for (RootInfo r : getRoots()) {
            if (r.getTotal() >= 0) {
                sum = Math.max(sum, 0L) + r.getTotal();
            }
        }
        return sum;
    }

    /** @return sum of the known root usable space, or a negative value if none is known */
    public long getDisksFree() {
        long sum = UNKNOWN;
        for (RootInfo r : getRoots()) {
            if (r.getTotal() >= 0 && r.getFree() >= 0) {
                sum = Math.max(sum, 0L) + r.getFree();
            }
        }
        return sum;
    }

    /** @return sum of the known root used space, or a negative value if none is known */
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
