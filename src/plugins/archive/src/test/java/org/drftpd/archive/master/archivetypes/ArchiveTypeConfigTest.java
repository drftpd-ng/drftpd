package org.drftpd.archive.master.archivetypes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class ArchiveTypeConfigTest {

    private static final String DEF = "(?i)(.*)(\\.|-|_)S\\d.*";

    @Test
    void missingAndBlankUseDefault() {
        assertEquals(DEF, ArchiveType.resolveRegex(null, DEF, true, "1.rlsregex"));
        assertEquals(DEF, ArchiveType.resolveRegex("", DEF, true, "1.rlsregex"));
        assertEquals(DEF, ArchiveType.resolveRegex("   ", DEF, false, "1.scansubdirsregex"));
    }

    @Test
    void invalidUsesDefault() {
        assertEquals(DEF, ArchiveType.resolveRegex("(unclosed", DEF, true, "1.rlsregex"));
        assertEquals(DEF, ArchiveType.resolveRegex("[", DEF, false, "1.scansubdirsregex"));
    }

    @Test
    void noCapturingGroupUsesDefaultWhenGroupRequired() {
        assertEquals(DEF, ArchiveType.resolveRegex(".*", DEF, true, "1.rlsregex"));
        assertEquals(".*", ArchiveType.resolveRegex(".*", DEF, false, "1.scansubdirsregex"));
    }

    @Test
    void validCustomIsUsedTrimmed() {
        assertEquals("(.*)-E\\d", ArchiveType.resolveRegex(" (.*)-E\\d ", DEF, true, "1.rlsregex"));
    }

    @Test
    void rootAndBaseAreStructureDirs() {
        assertTrue(ArchiveType.isSectionStructureDir("/", "/TV", "/TV"));
        assertTrue(ArchiveType.isSectionStructureDir("/TV", "/TV", "/TV"));
        assertFalse(ArchiveType.isSectionStructureDir("/TV/Some.Show.S01-GRP", "/TV", "/TV"));
    }

    @Test
    void currentDatedDirIsStructureDirButOldOnesAreNot() {
        assertTrue(ArchiveType.isSectionStructureDir("/TV", "/TV", "/TV/2026-11-20"));
        assertTrue(ArchiveType.isSectionStructureDir("/TV/2026-11-20", "/TV", "/TV/2026-11-20"));
        assertFalse(ArchiveType.isSectionStructureDir("/TV/2026-11-19", "/TV", "/TV/2026-11-20"));
        assertFalse(ArchiveType.isSectionStructureDir("/TV/2026-11-20/Rel-GRP", "/TV", "/TV/2026-11-20"));
    }

    @Test
    void multiLevelDatedDirAncestorsAreStructureDirs() {
        assertTrue(ArchiveType.isSectionStructureDir("/TV/2026", "/TV", "/TV/2026/11-20"));
        assertFalse(ArchiveType.isSectionStructureDir("/TV/2025", "/TV", "/TV/2026/11-20"));
        assertFalse(ArchiveType.isSectionStructureDir("/TV/2026x", "/TV", "/TV/2026/11-20"));
    }
}
