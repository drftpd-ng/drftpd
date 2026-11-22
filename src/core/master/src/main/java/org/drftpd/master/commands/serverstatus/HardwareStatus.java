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
package org.drftpd.master.commands.serverstatus;

import org.drftpd.common.slave.SystemInfo;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.slavemanagement.SystemInfoReply;
import org.drftpd.master.util.Time;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Pure formatting helpers for the {@code hardware} section of SITE STATUS: they turn a {@link SystemInfo} into
 * the environment maps used by the serverstatus theme (keys {@code status.hw.*}).
 */
public final class HardwareStatus {

    /** Name used on the command line and in the output for the master itself. */
    public static final String MASTER = "master";

    /** Directory whose filesystem is reported as the master's single "root". */
    public static final String MASTER_ROOT = "userdata";

    /** How long to wait for a slave to answer. */
    public static final long SLAVE_TIMEOUT_MILLIS = 10000L;

    public static final String NOT_AVAILABLE = "N/A";

    private HardwareStatus() {
    }

    /** @return a ratio in [0,1] as a percentage with one decimal, or N/A when negative */
    public static String percent(double ratio) {
        if (ratio < 0 || Double.isNaN(ratio)) {
            return NOT_AVAILABLE;
        }
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0);
    }

    /** @return part / total as a percentage with one decimal, or N/A when unknown */
    public static String percent(long part, long total) {
        if (part < 0 || total <= 0) {
            return NOT_AVAILABLE;
        }
        return percent((double) part / (double) total);
    }

    /** @return the formatted size, or N/A for a negative (unknown) value */
    public static String bytes(long value) {
        return value < 0 ? NOT_AVAILABLE : Bytes.formatBytes(value);
    }

    public static String text(String value) {
        return (value == null || value.isEmpty()) ? NOT_AVAILABLE : value;
    }

    private static String loadAverage(double load) {
        return load < 0 ? NOT_AVAILABLE : String.format(Locale.ROOT, "%.2f", load);
    }

    private static String number(long value) {
        return value < 0 ? NOT_AVAILABLE : String.valueOf(value);
    }

    /** Environment of the one-line summary. */
    public static Map<String, Object> summaryEnv(String name, SystemInfo si) {
        Map<String, Object> env = new HashMap<>();
        env.put("hw.name", name);
        env.put("hw.os", text(si.getOsName()));
        env.put("hw.os.arch", text(si.getOsArch()));
        env.put("hw.cores", number(si.getAvailableProcessors()));
        env.put("hw.cpu", percent(si.getSystemCpuLoad()));
        env.put("hw.ram.used", bytes(si.getUsedMemory()));
        env.put("hw.ram.total", bytes(si.getTotalMemory()));
        env.put("hw.ram.percent", percent(si.getUsedMemory(), si.getTotalMemory()));
        env.put("hw.disk.used", bytes(si.getDisksUsed()));
        env.put("hw.disk.total", bytes(si.getDisksTotal()));
        env.put("hw.disk.percent", percent(si.getDisksUsed(), si.getDisksTotal()));
        env.put("hw.filesystems", si.getFilesystemCount());
        return env;
    }

    /** Environment of the detail lines (status.hw.detail.*, except the per root lines). */
    public static Map<String, Object> detailEnv(String name, SystemInfo si) {
        Map<String, Object> env = summaryEnv(name, si);
        env.put("hw.version", text(si.getDrftpdVersion()));
        env.put("hw.os.version", text(si.getOsVersion()));
        env.put("hw.loadavg", loadAverage(si.getSystemLoadAverage()));
        env.put("hw.cpu.process", percent(si.getProcessCpuLoad()));
        env.put("hw.ram.free", bytes(si.getFreeMemory()));
        env.put("hw.swap.used", bytes(si.getUsedSwap()));
        env.put("hw.swap.free", bytes(si.getFreeSwap()));
        env.put("hw.swap.total", bytes(si.getTotalSwap()));
        env.put("hw.java.version", text(si.getJavaVersion()));
        env.put("hw.java.vendor", text(si.getJavaVendor()));
        env.put("hw.heap.used", bytes(si.getHeapUsed()));
        env.put("hw.heap.max", bytes(si.getHeapMax()));
        env.put("hw.uptime", si.getProcessUptime() < 0 ? NOT_AVAILABLE : Time.formatTime(si.getProcessUptime()));
        env.put("hw.disk.free", bytes(si.getDisksFree()));
        env.put("hw.roots", si.getRoots().size());
        return env;
    }

    /**
     * Environments of the per root lines (status.hw.detail.root).
     *
     * @param showPaths true to show the configured root path, false to show "root N" instead (IRC output may go
     *                  to a channel, the filesystem layout of the slaves is not something to publish there)
     */
    public static List<Map<String, Object>> rootEnvs(String name, SystemInfo si, boolean showPaths) {
        List<Map<String, Object>> list = new ArrayList<>();
        int i = 1;
        for (SystemInfo.RootInfo root : si.getRoots()) {
            Map<String, Object> env = new HashMap<>();
            env.put("hw.name", name);
            env.put("hw.root.index", i);
            env.put("hw.root.label", showPaths ? text(root.getPath()) : "root " + i);
            env.put("hw.root.used", bytes(root.getUsed()));
            env.put("hw.root.free", bytes(root.getFree()));
            env.put("hw.root.total", bytes(root.getTotal()));
            env.put("hw.root.percent", percent(root.getUsed(), root.getTotal()));
            list.add(env);
            i++;
        }
        return list;
    }

    /** @return the theme key for a reply that carries no information */
    public static String failureKey(SystemInfoReply.Status status) {
        switch (status) {
            case UNSUPPORTED:
                return "status.hw.unsupported";
            case OFFLINE:
                return "status.hw.offline";
            case TIMEOUT:
                return "status.hw.timeout";
            default:
                return "status.hw.error";
        }
    }
}
