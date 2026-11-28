package org.drftpd.mirror.master;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;
import java.util.Properties;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class MirrorSettingsTest {

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    private static MirrorSettings load(Properties p) {
        return new MirrorSettings(p, name -> true);
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void validEntriesAreRead() {
        MirrorSettings s = load(props(
                "pre.unmirror.time", "30",
                "1.nbrOfMirrors", "2",
                "1.priority", "5",
                "1.path1", "/A/.*",
                "1.path2", "/B/.*",
                "1.excludePath1", ".*/sample/.*",
                "1.slaves", "S1 S2",
                "1.unmirrorExclude", ".*nfo",
                "2.nbrOfMirrors", "3",
                "2.path1", "/C/.*",
                "2.excludeSlaves", "S9"));
        assertEquals(30L * 60000L, s.getUnmirrorTime());
        assertEquals(2, s.getConfigurations().size());
        MirrorSettings.MirrorConfiguration c1 = s.getConfigurations().get(0);
        assertEquals(2, c1.getNbrOfMirrors());
        assertEquals(5, c1.getPriority());
        assertEquals(List.of("/A/.*", "/B/.*"), c1.getPaths());
        assertEquals(List.of(".*/sample/.*"), c1.getExcludedPaths());
        assertEquals(List.of("S1", "S2"), c1.getSlaves());
        MirrorSettings.MirrorConfiguration c2 = s.getConfigurations().get(1);
        assertEquals(3, c2.getNbrOfMirrors());
        assertEquals(3, c2.getPriority(), "priority defaults to 3");
        assertEquals(List.of("S9"), c2.getExcludedSlaves());
        assertEquals(List.of(".*nfo"), s.getUnmirrorExcludePaths());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void badNbrOfMirrorsIsSkippedAndTheNextEntryStillLoads() {
        MirrorSettings s = load(props(
                "1.nbrOfMirrors", "abc", "1.path1", "/A/.*",
                "2.nbrOfMirrors", "1", "2.path1", "/B/.*",
                "3.nbrOfMirrors", "2", "3.path1", "/C/.*"));
        assertEquals(1, s.getConfigurations().size());
        assertEquals(List.of("/C/.*"), s.getConfigurations().get(0).getPaths());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void badPriorityIsSkippedAndTheNextEntryStillLoads() {
        MirrorSettings s = load(props(
                "1.nbrOfMirrors", "2", "1.priority", "high", "1.path1", "/A/.*",
                "2.nbrOfMirrors", "2", "2.priority", "0", "2.path1", "/B/.*",
                "3.nbrOfMirrors", "2", "3.priority", "1", "3.path1", "/C/.*"));
        assertEquals(1, s.getConfigurations().size());
        assertEquals(1, s.getConfigurations().get(0).getPriority());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void missingValuesUseDefaultsAndReadingStopsAtTheFirstGap() {
        MirrorSettings s = load(props(
                "1.nbrOfMirrors", "2", "1.path1", "/A/.*",
                "3.nbrOfMirrors", "2", "3.path1", "/C/.*"));
        assertEquals(60L * 60000L, s.getUnmirrorTime());
        assertEquals(1, s.getConfigurations().size());
        assertTrue(s.getConfigurations().get(0).getSlaves().isEmpty());
        assertTrue(s.getUnmirrorExcludePaths().isEmpty());
        assertTrue(load(props()).getConfigurations().isEmpty());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void badUnmirrorTimeFallsBackToTheDefault() {
        assertEquals(60L * 60000L, load(props("pre.unmirror.time", "soon")).getUnmirrorTime());
    }

    @Test
    @Timeout(value = 10, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    public void reloadReplacesThePreviousConfiguration() {
        MirrorSettings s = load(props("1.nbrOfMirrors", "2", "1.path1", "/A/.*"));
        s.reload(props("1.nbrOfMirrors", "x"), name -> true);
        assertTrue(s.getConfigurations().isEmpty());
    }
}
