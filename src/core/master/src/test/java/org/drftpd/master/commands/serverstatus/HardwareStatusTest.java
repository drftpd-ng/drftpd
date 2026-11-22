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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class HardwareStatusTest {

    @Test
    public void percentHasOneDecimalAndHandlesUnknown() {
        assertEquals("12.3%", HardwareStatus.percent(0.1234));
        assertEquals("0.0%", HardwareStatus.percent(0.0));
        assertEquals("100.0%", HardwareStatus.percent(1.0));
        assertEquals("N/A", HardwareStatus.percent(-1.0));
        assertEquals("N/A", HardwareStatus.percent(Double.NaN));
        assertEquals("25.0%", HardwareStatus.percent(25, 100));
        assertEquals("N/A", HardwareStatus.percent(-1, 100));
        assertEquals("N/A", HardwareStatus.percent(10, 0));
    }

    @Test
    public void bytesAndTextHandleUnknown() {
        assertEquals("N/A", HardwareStatus.bytes(-1));
        assertEquals(Bytes.formatBytes(1024L * 1024L), HardwareStatus.bytes(1024L * 1024L));
        assertEquals("N/A", HardwareStatus.text(null));
        assertEquals("N/A", HardwareStatus.text(""));
        assertEquals("Linux", HardwareStatus.text("Linux"));
    }

    @Test
    public void summaryOfAnEmptyInfoIsAllUnknown() {
        Map<String, Object> env = HardwareStatus.summaryEnv("slave1", new SystemInfo());
        assertEquals("slave1", env.get("hw.name"));
        for (String key : Arrays.asList("hw.os", "hw.cores", "hw.cpu", "hw.ram.used", "hw.ram.total",
                "hw.disk.used", "hw.disk.total")) {
            assertEquals("N/A", env.get(key), key);
        }
    }

    @Test
    public void detailOfACollectedInfoIsComplete(@TempDir Path dir) {
        SystemInfo si = SystemInfo.collect("DrFTPD x", List.of(dir.toString()));
        Map<String, Object> env = HardwareStatus.detailEnv("master", si);
        assertEquals("DrFTPD x", env.get("hw.version"));
        assertEquals(si.getOsName(), env.get("hw.os"));
        assertEquals(String.valueOf(si.getAvailableProcessors()), env.get("hw.cores"));
        assertEquals(Bytes.formatBytes(si.getDisksTotal()), env.get("hw.disk.total"));
        assertEquals(1, env.get("hw.roots"));
        for (String key : Arrays.asList("hw.os.version", "hw.loadavg", "hw.cpu.process", "hw.ram.free",
                "hw.swap.used", "hw.swap.total", "hw.java.version", "hw.java.vendor", "hw.heap.used",
                "hw.heap.max", "hw.uptime", "hw.disk.free", "hw.disk.percent", "hw.ram.percent")) {
            assertNotNull(env.get(key), key);
        }
        String cpu = env.get("hw.cpu").toString();
        assertTrue(cpu.equals("N/A") || cpu.matches("\\d+\\.\\d%"), cpu);
    }

    @Test
    public void rootPathsAreHiddenWhenAsked(@TempDir Path dir) throws Exception {
        Path a = Files.createDirectory(dir.resolve("a"));
        Path b = Files.createDirectory(dir.resolve("b"));
        SystemInfo si = SystemInfo.collect("v", List.of(a.toString(), b.toString()));

        List<Map<String, Object>> shown = HardwareStatus.rootEnvs("slave1", si, true);
        assertEquals(2, shown.size());
        assertEquals(a.toString(), shown.get(0).get("hw.root.label"));
        assertEquals(b.toString(), shown.get(1).get("hw.root.label"));
        assertEquals(2, shown.get(1).get("hw.root.index"));

        List<Map<String, Object>> hidden = HardwareStatus.rootEnvs("slave1", si, false);
        assertEquals("root 1", hidden.get(0).get("hw.root.label"));
        assertEquals("root 2", hidden.get(1).get("hw.root.label"));
        for (Map<String, Object> env : hidden) {
            for (Object value : env.values()) {
                assertFalse(value.toString().contains(dir.toString()), "path leaked: " + value);
            }
        }
    }

    @Test
    public void failureKeys() {
        assertEquals("status.hw.unsupported", HardwareStatus.failureKey(SystemInfoReply.Status.UNSUPPORTED));
        assertEquals("status.hw.offline", HardwareStatus.failureKey(SystemInfoReply.Status.OFFLINE));
        assertEquals("status.hw.timeout", HardwareStatus.failureKey(SystemInfoReply.Status.TIMEOUT));
        assertEquals("status.hw.error", HardwareStatus.failureKey(SystemInfoReply.Status.ERROR));
    }

    @Test
    public void hardwareTargetParsing() {
        List<String> tokens = new ArrayList<>(List.of("hw", "Slave01"));
        assertEquals("Slave01", ServerStatus.removeHardwareTarget(tokens));
        assertEquals(List.of("hw"), tokens);

        tokens = new ArrayList<>(List.of("os", "HARDWARE", "master", "vm"));
        assertEquals("master", ServerStatus.removeHardwareTarget(tokens));
        assertEquals(List.of("os", "HARDWARE", "vm"), tokens);

        // a section name after hardware is a section, not a target
        tokens = new ArrayList<>(List.of("hardware", "os"));
        assertNull(ServerStatus.removeHardwareTarget(tokens));
        assertEquals(List.of("hardware", "os"), tokens);

        tokens = new ArrayList<>(List.of("hw"));
        assertNull(ServerStatus.removeHardwareTarget(tokens));

        // existing sections are untouched
        tokens = new ArrayList<>(List.of("os", "vm", "all"));
        assertNull(ServerStatus.removeHardwareTarget(tokens));
        assertEquals(List.of("os", "vm", "all"), tokens);
    }

    @Test
    public void themesDefineEveryHardwareKey() throws Exception {
        List<String> keys = List.of("status.hw.summary", "status.hw.offline", "status.hw.unsupported",
                "status.hw.timeout", "status.hw.error", "status.hw.notfound", "status.hw.detail.header",
                "status.hw.detail.os", "status.hw.detail.cpu", "status.hw.detail.memory", "status.hw.detail.jvm",
                "status.hw.detail.disks", "status.hw.detail.root");
        for (String theme : List.of("ftp", "irc")) {
            Properties p = new Properties();
            try (InputStream in = getClass().getResourceAsStream(
                    "/master/config/themes/" + theme + "/serverstatus.theme.default")) {
                assertNotNull(in, theme);
                p.load(in);
            }
            for (String key : keys) {
                assertNotNull(p.getProperty(key), theme + ": " + key);
            }
        }
    }

    @Test
    public void testDuration() {
        assertEquals("0s", HardwareStatus.duration(0));
        assertEquals("36s", HardwareStatus.duration(36691));
        assertEquals("59s", HardwareStatus.duration(59999));
        assertEquals("1m 4s", HardwareStatus.duration(64000));
        assertEquals("1m 0s", HardwareStatus.duration(60000));
        assertEquals("2h 5m", HardwareStatus.duration(2 * 3600000L + 5 * 60000L + 30000L));
        assertEquals("3d 4h 5m", HardwareStatus.duration(((3 * 24 + 4) * 60 + 5) * 60000L));
        assertEquals("N/A", HardwareStatus.duration(-1));
    }
}
