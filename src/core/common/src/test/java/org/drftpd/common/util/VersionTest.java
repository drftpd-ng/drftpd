package org.drftpd.common.util;

import org.junit.jupiter.api.Test;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class VersionTest {

    private static Properties props(String version, String commit) {
        Properties p = new Properties();
        if (version != null) {
            p.setProperty("version", version);
        }
        if (commit != null) {
            p.setProperty("commit", commit);
        }
        return p;
    }

    @Test
    void displayWithCommit() {
        Version v = Version.from(props("4.0.12-SNAPSHOT", "87d6ba0"));
        assertEquals("4.0.12-SNAPSHOT", v.version());
        assertEquals("87d6ba0", v.commit());
        assertEquals("DrFTPD 4.0.12-SNAPSHOT (87d6ba0)", v.display());
        assertEquals("4.0.12-SNAPSHOT (87d6ba0)", v.fullVersion());
    }

    @Test
    void displayWithoutCommit() {
        Version v = Version.from(props("4.0.12-SNAPSHOT", ""));
        assertEquals("", v.commit());
        assertEquals("DrFTPD 4.0.12-SNAPSHOT", v.display());
    }

    @Test
    void commitKeyAbsent() {
        assertEquals("DrFTPD 4.0.12", Version.from(props("4.0.12", null)).display());
    }

    @Test
    void unresolvedPlaceholderCommitIsIgnored() {
        // Maven leaves the placeholder when git-commit-id could not read a repository (tarball build)
        Version v = Version.from(props("4.0.12-beta2", "${git.commit.id.abbrev}"));
        assertEquals("", v.commit());
        assertEquals("DrFTPD 4.0.12-beta2", v.display());
    }

    @Test
    void unresolvedVersionIsUnknown() {
        Version v = Version.from(props("${project.version}", "${git.commit.id.abbrev}"));
        assertEquals("unknown", v.version());
        assertEquals("DrFTPD unknown", v.display());
    }

    @Test
    void missingPropertiesFallBack() {
        Version v = Version.from(new Properties());
        assertEquals("unknown", v.version());
        assertEquals("", v.commit());
        assertEquals("DrFTPD unknown", v.display());
    }

    @Test
    void valuesAreTrimmed() {
        assertEquals("DrFTPD 1.2.3 (abc1234)", Version.from(props(" 1.2.3 ", " abc1234\t")).display());
    }

    @Test
    void buildResourceIsFilteredAndConsistent() {
        // version.properties is filtered at build time: the real version must be resolved
        assertNotEquals("unknown", Version.getVersion());
        assertFalse(Version.getVersion().contains("${"));
        assertFalse(Version.getCommit().contains("${"));
        assertTrue(Version.getDisplay().startsWith("DrFTPD " + Version.getVersion()));
        assertTrue(Version.getDisplay().endsWith(Version.getFullVersion()));
    }
}
