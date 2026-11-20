package org.drftpd.autofreespace.master;

import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

public class AutoFreeSpacePathTest {

    // Directory tree as "path -> child names", rooted at "/"
    private static final Map<String, List<String>> TREE = Map.of(
            "/", List.of("MP3", "TV"),
            "/MP3", List.of("1101", "1102", "1103"),
            "/MP3/1101", List.of(),
            "/MP3/1102", List.of("Some.Release-GRP"),
            "/MP3/1103", List.of("Other.Release-GRP"),
            "/TV", List.of(),
            "/MP3/1102/Some.Release-GRP", List.of(),
            "/MP3/1103/Other.Release-GRP", List.of());

    private static String join(String dir, String name) {
        return dir.equals("/") ? "/" + name : dir + "/" + name;
    }

    private static List<String> expand(String path) {
        return AutoFreeSpace.expandPath(path, "/",
                dir -> TREE.getOrDefault(dir, List.of()).stream().map(n -> join(dir, n)).toList(),
                (dir, name) -> TREE.getOrDefault(dir, List.of()).contains(name) ? join(dir, name) : null);
    }

    @Test
    public void exactPathResolvesToThatDirectory() {
        assertEquals(List.of("/MP3/1102"), expand("/MP3/1102"));
    }

    @Test
    public void missingPathResolvesToNothing() {
        assertTrue(expand("/MP3/9999").isEmpty());
        assertTrue(expand("/NOPE/*").isEmpty());
    }

    @Test
    public void wildcardMatchesEverySubdirectoryNotOnlyTheOldest() {
        // Before the fix only one subdirectory was used; an empty one (1101) blocked the others forever.
        assertEquals(List.of("/MP3/1101", "/MP3/1102", "/MP3/1103"), expand("/MP3/*"));
    }

    @Test
    public void wildcardInTheMiddle() {
        assertEquals(List.of("/MP3/1102/Some.Release-GRP", "/MP3/1103/Other.Release-GRP"), expand("/MP3/*/*"));
    }

    @Test
    public void wildcardOnEmptyDirectoryResolvesToNothing() {
        assertTrue(expand("/TV/*").isEmpty());
    }

    private static Properties props(String... kv) {
        Properties p = new Properties();
        for (int i = 0; i < kv.length; i += 2) {
            p.setProperty(kv[i], kv[i + 1]);
        }
        return p;
    }

    @Test
    public void wipeAfterIsRequiredInDateMode() {
        // A missing wipeAfter used to default to 0, making every release under the entry deletable.
        assertNull(AutoFreeSpaceSettings.parseWipeAfter(props(), 1, AutoFreeSpaceSettings.MODE_DATE));
        assertNull(AutoFreeSpaceSettings.parseWipeAfter(props("1.wipeAfter", "abc"), 1, AutoFreeSpaceSettings.MODE_DATE));
        assertNull(AutoFreeSpaceSettings.parseWipeAfter(props("1.wipeAfter", "-5"), 1, AutoFreeSpaceSettings.MODE_DATE));
    }

    @Test
    public void wipeAfterIsConvertedFromMinutes() {
        assertEquals(43200L * 60000L,
                AutoFreeSpaceSettings.parseWipeAfter(props("1.wipeAfter", " 43200 "), 1, AutoFreeSpaceSettings.MODE_DATE));
    }

    @Test
    public void wipeAfterIsOptionalOutsideDateMode() {
        assertEquals(0L, AutoFreeSpaceSettings.parseWipeAfter(props(), 1, AutoFreeSpaceSettings.MODE_SPACE));
    }

    private static boolean allSections(boolean wipeAllSections, Properties p) {
        return AutoFreeSpaceSettings.useAllSections(AutoFreeSpaceSettings.MODE_SPACE, wipeAllSections,
                AutoFreeSpaceSettings.countEntries(p));
    }

    @Test
    public void noEntryDoesNotWipeAllSectionsByDefault() {
        assertFalse(allSections(false, props()));
    }

    @Test
    public void noEntryWipesAllSectionsWhenEnabled() {
        assertTrue(allSections(true, props()));
        // Only in space mode
        assertFalse(AutoFreeSpaceSettings.useAllSections(AutoFreeSpaceSettings.MODE_DATE, true, 0));
    }

    @Test
    public void unknownSectionNameNeverWidensToAllSections() {
        // A typo in the only section name leaves no valid entry, which used to fall back to every section.
        Properties p = props("1.section", "MP3Z");
        assertTrue(AutoFreeSpaceSettings.parseEntries(p, AutoFreeSpaceSettings.MODE_SPACE, "MP3"::equalsIgnoreCase).isEmpty());
        assertEquals(1, AutoFreeSpaceSettings.countEntries(p));
        assertFalse(allSections(true, p));
    }

    @Test
    public void rootPathIsRefused() {
        for (String root : List.of("/", "//", "/.", "", " ", "/MP3/..", "/MP3/../TV", "/./MP3")) {
            Properties p = props("1.path", root);
            assertTrue(AutoFreeSpaceSettings.parseEntries(p, AutoFreeSpaceSettings.MODE_SPACE, s -> true).isEmpty(),
                    "path [" + root + "] must be refused");
            // a refused entry still counts as written, so it never widens to all sections either
            assertFalse(allSections(true, p));
        }
        assertTrue(AutoFreeSpaceSettings.isUsablePath("/MP3"));
        assertTrue(AutoFreeSpaceSettings.isUsablePath("MP3/*"));
        assertTrue(AutoFreeSpaceSettings.isUsablePath("/*"));
    }

    @Test
    public void rootIsNeverABaseDirectory() {
        // e.g. a section whose base directory is "/", or a path that resolves to the root
        assertEquals(List.of("/MP3"), AutoFreeSpace.withoutRoot(List.of("/", "/MP3"), "/"::equals));
        assertEquals(List.of(), AutoFreeSpace.withoutRoot(expand("/"), "/"::equals));
    }

    @Test
    public void knownSectionNameIsLoaded() {
        Properties p = props("1.section", "MP3", "2.path", "/TV/*");
        assertEquals(Set.of("MP3", AutoFreeSpaceSettings.PATH_KEY_PREFIX + "/TV/*"),
                AutoFreeSpaceSettings.parseEntries(p, AutoFreeSpaceSettings.MODE_SPACE, "MP3"::equalsIgnoreCase).keySet());
        assertFalse(allSections(true, p));
    }
}
