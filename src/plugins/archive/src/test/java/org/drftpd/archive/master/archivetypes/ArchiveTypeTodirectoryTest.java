package org.drftpd.archive.master.archivetypes;

import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArchiveTypeTodirectoryTest {
    private static SectionInterface section(String name, String path) {
        return new SectionInterface() {
            public DirectoryHandle getBaseDirectory() {
                return new DirectoryHandle(path);
            }

            public DirectoryHandle getCurrentDirectory() {
                return new DirectoryHandle(path);
            }

            public Set<DirectoryHandle> getDirectories() {
                return Set.of();
            }

            public String getName() {
                return name;
            }

            public String getColor() {
                return "15";
            }
        };
    }

    @Test
    void realSectionIsUsed() {
        assertTrue(ArchiveType.isExistingSection(section("TV", "/TV")));
    }

    @Test
    void emptyStandInOfConfSectionManagerIsNotASection() {
        assertFalse(ArchiveType.isExistingSection(section("", "/")));
    }

    @Test
    void rootStandInOfDefaultSectionManagerIsNotASection() {
        // the root's name is "/", so the default section manager's fallback for an unknown name is not empty
        assertFalse(ArchiveType.isExistingSection(section("/", "/")));
    }
}
