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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class SystemInfoTest {

    @Test
    public void collectReportsSaneValues() {
        SystemInfo si = SystemInfo.collect("DrFTPD test", null);

        assertEquals("DrFTPD test", si.getDrftpdVersion());
        assertNotNull(si.getOsName());
        assertNotNull(si.getOsArch());
        assertNotNull(si.getJavaVersion());
        assertTrue(si.getAvailableProcessors() >= 1);
        // CPU loads are a ratio in [0,1] or -1 when unknown (load average is -1 on Windows)
        assertTrue(si.getSystemCpuLoad() == -1 || (si.getSystemCpuLoad() >= 0 && si.getSystemCpuLoad() <= 1));
        assertTrue(si.getProcessCpuLoad() == -1 || (si.getProcessCpuLoad() >= 0 && si.getProcessCpuLoad() <= 1));
        assertTrue(si.getSystemLoadAverage() == -1 || si.getSystemLoadAverage() >= 0);
        // memory: either unknown (-1) or consistent
        if (si.getTotalMemory() >= 0) {
            assertTrue(si.getTotalMemory() > 0);
            assertTrue(si.getFreeMemory() <= si.getTotalMemory());
            assertTrue(si.getUsedMemory() >= 0);
        }
        assertTrue(si.getHeapUsed() > 0);
        assertTrue(si.getProcessUptime() >= 0);
        assertTrue(si.getRoots().isEmpty());
        assertEquals(SystemInfo.UNKNOWN, si.getDisksTotal());
        assertEquals(SystemInfo.UNKNOWN, si.getDisksUsed());
    }

    @Test
    public void rootsAreBuiltFromGivenPathsInOrder(@TempDir Path dir) throws IOException {
        Path a = Files.createDirectory(dir.resolve("a"));
        Path b = Files.createDirectory(dir.resolve("b"));
        Path missing = dir.resolve("does-not-exist");

        SystemInfo si = SystemInfo.collect("v", Arrays.asList(a.toString(), null, b.toString(), missing.toString()));
        List<SystemInfo.RootInfo> roots = si.getRoots();

        assertEquals(3, roots.size(), "null paths are skipped");
        assertEquals(a.toString(), roots.get(0).getPath());
        assertEquals(b.toString(), roots.get(1).getPath());
        assertEquals(missing.toString(), roots.get(2).getPath());

        for (SystemInfo.RootInfo r : roots.subList(0, 2)) {
            assertTrue(r.getTotal() > 0);
            assertTrue(r.getFree() >= 0 && r.getFree() <= r.getTotal());
            assertEquals(r.getTotal() - r.getFree(), r.getUsed());
        }
        // a path that cannot be read is reported as unknown, never as 0 bytes
        assertEquals(SystemInfo.UNKNOWN, roots.get(2).getTotal());
        assertEquals(SystemInfo.UNKNOWN, roots.get(2).getFree());
        assertEquals(SystemInfo.UNKNOWN, roots.get(2).getUsed());

        // totals only add up the known roots, and a filesystem shared by two roots counts once
        assertEquals(roots.get(0).getTotal(), si.getDisksTotal());
        assertEquals(roots.get(0).getFree(), si.getDisksFree());
        assertEquals(1, si.getFilesystemCount());
        assertEquals(si.getDisksTotal() - si.getDisksFree(), si.getDisksUsed());
    }

    @Test
    public void rootsListIsImmutable(@TempDir Path dir) {
        SystemInfo si = SystemInfo.collect("v", Collections.singletonList(dir.toString()));
        assertThrows(UnsupportedOperationException.class, () -> si.getRoots().clear());
    }

    @Test
    public void unknownValuesOfAnEmptyInstance() {
        SystemInfo si = new SystemInfo();
        assertNull(si.getOsName());
        assertEquals(-1, si.getAvailableProcessors());
        assertEquals(-1, si.getSystemCpuLoad());
        assertEquals(SystemInfo.UNKNOWN, si.getUsedMemory());
        assertEquals(SystemInfo.UNKNOWN, si.getUsedSwap());
        assertTrue(si.getRoots().isEmpty());

        SystemInfo.RootInfo r = new SystemInfo.RootInfo("/x", -1, 100);
        assertEquals(SystemInfo.UNKNOWN, r.getUsed());
    }

    @Test
    public void survivesJavaSerialization(@TempDir Path dir) throws Exception {
        SystemInfo si = SystemInfo.collect("v1", Collections.singletonList(dir.toString()));

        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(si);
        }
        SystemInfo copy;
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(bos.toByteArray()))) {
            copy = (SystemInfo) in.readObject();
        }

        assertEquals(si.getOsName(), copy.getOsName());
        assertEquals(si.getAvailableProcessors(), copy.getAvailableProcessors());
        assertEquals(si.getTotalMemory(), copy.getTotalMemory());
        assertEquals("v1", copy.getDrftpdVersion());
        assertEquals(1, copy.getRoots().size());
        assertEquals(dir.toString(), copy.getRoots().get(0).getPath());
    }

    @Test
    public void rootsOnTheSameFilesystemAreCountedOnce(@TempDir Path dir) throws Exception {
        Path a = Files.createDirectory(dir.resolve("a"));
        Path b = Files.createDirectory(dir.resolve("b"));
        SystemInfo one = SystemInfo.collect("v", List.of(a.toString()));
        SystemInfo two = SystemInfo.collect("v", List.of(a.toString(), b.toString()));

        assertEquals(2, two.getRoots().size());
        assertNotNull(two.getRoots().get(0).getFsKey());
        assertEquals(two.getRoots().get(0).getFsKey(), two.getRoots().get(1).getFsKey());
        assertEquals(1, two.getFilesystemCount());
        assertEquals(one.getRoots().get(0).getTotal(), two.getDisksTotal());
    }

    @Test
    public void sameDirectoryTwiceIsOneFilesystem(@TempDir Path dir) {
        SystemInfo si = SystemInfo.collect("v", List.of(dir.toString(), dir.toString()));
        assertEquals(2, si.getRoots().size());
        assertEquals(1, si.getFilesystemCount());
        assertEquals(si.getRoots().get(0).getTotal(), si.getDisksTotal());
    }

    @Test
    public void totalsSumDistinctFilesystemsOnly() {
        SystemInfo si = new SystemInfo();
        List<SystemInfo.RootInfo> roots = List.of(
                new SystemInfo.RootInfo("/a", 10, 100, "dev:1"),
                new SystemInfo.RootInfo("/b", 10, 100, "dev:1"),
                new SystemInfo.RootInfo("/c", 50, 200, "dev:2"),
                new SystemInfo.RootInfo("/d", 5, 20, null),
                new SystemInfo.RootInfo("/e", -1, -1, null));
        setRoots(si, roots);
        assertEquals(3, si.getFilesystemCount());
        assertEquals(320, si.getDisksTotal());
        assertEquals(65, si.getDisksFree());
        assertEquals(255, si.getDisksUsed());
        assertEquals(5, si.getRoots().size());
    }

    private static void setRoots(SystemInfo si, List<SystemInfo.RootInfo> roots) {
        try {
            java.lang.reflect.Field f = SystemInfo.class.getDeclaredField("_roots");
            f.setAccessible(true);
            f.set(si, roots);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    @Test
    public void primeOnceSamplesOnlyTheFirstTime() {
        java.util.concurrent.atomic.AtomicBoolean flag = new java.util.concurrent.atomic.AtomicBoolean(false);
        int[] samples = {0};
        int[] pauses = {0};
        assertTrue(SystemInfo.primeOnce(flag, () -> samples[0]++, () -> pauses[0]++));
        assertFalse(SystemInfo.primeOnce(flag, () -> samples[0]++, () -> pauses[0]++));
        assertEquals(1, samples[0]);
        assertEquals(1, pauses[0]);
    }

    @Test
    public void primeOnceSurvivesAFailingSampler() {
        java.util.concurrent.atomic.AtomicBoolean flag = new java.util.concurrent.atomic.AtomicBoolean(false);
        assertTrue(SystemInfo.primeOnce(flag, () -> {
            throw new UnsupportedOperationException();
        }, null));
        assertTrue(flag.get());
    }

    @Test
    public void primeCpuLoadIsHarmless() {
        SystemInfo.primeCpuLoad();
        SystemInfo.primeCpuLoad();
        SystemInfo si = SystemInfo.collect("v", null);
        assertTrue(si.getSystemCpuLoad() <= 1.0);
    }
}
