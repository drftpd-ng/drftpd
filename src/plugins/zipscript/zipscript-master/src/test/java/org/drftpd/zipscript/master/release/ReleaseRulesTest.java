package org.drftpd.zipscript.master.release;

import org.drftpd.master.permissions.GlobPathPermission;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.zipscript.master.release.ReleaseRules.Kind;
import org.drftpd.zipscript.master.release.ReleaseRules.ReleaseDir;
import org.drftpd.zipscript.master.release.ReleaseRules.State;
import org.junit.jupiter.api.Test;

import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the 'downloadinc' and 'deletecomplete' rules (#595).
 */
public class ReleaseRulesTest {

    private static final class FakeDir implements ReleaseDir {
        private final String _name;
        private final Kind _kind;
        private final FakeDir _parent;

        FakeDir(String name, Kind kind, FakeDir parent) {
            _name = name;
            _kind = kind;
            _parent = parent;
        }

        public Kind kind() {
            return _kind;
        }

        public ReleaseDir parent() {
            return _parent;
        }

        @Override
        public String toString() {
            return _name;
        }
    }

    @Test
    void stateOfCounts() {
        assertEquals(State.COMPLETE, ReleaseRules.stateOf(10, 0));
        assertEquals(State.INCOMPLETE, ReleaseRules.stateOf(10, 1));
        assertEquals(State.INCOMPLETE, ReleaseRules.stateOf(10, 10));
        // empty sfv / bogus DIZ total: never acted upon
        assertEquals(State.UNKNOWN, ReleaseRules.stateOf(0, 0));
        assertEquals(State.UNKNOWN, ReleaseRules.stateOf(-1, 0));
    }

    @Test
    void downloadDecision() {
        assertTrue(ReleaseRules.denyDownload(State.INCOMPLETE, false));
        assertFalse(ReleaseRules.denyDownload(State.INCOMPLETE, true), "exempt user");
        assertFalse(ReleaseRules.denyDownload(State.COMPLETE, false));
        assertFalse(ReleaseRules.denyDownload(State.UNKNOWN, false), "unknown state fails open");
    }

    @Test
    void deleteDecision() {
        assertTrue(ReleaseRules.denyDelete(State.COMPLETE, false));
        assertFalse(ReleaseRules.denyDelete(State.COMPLETE, true), "exempt user");
        assertFalse(ReleaseRules.denyDelete(State.INCOMPLETE, false));
        assertFalse(ReleaseRules.denyDelete(State.UNKNOWN, false), "unknown state fails open");
    }

    @Test
    void locateOwnDir() {
        FakeDir section = new FakeDir("/MP3", null, null);
        FakeDir rel = new FakeDir("rel", Kind.SFV, section);
        assertSame(rel, ReleaseRules.locate(rel));
    }

    @Test
    void locateZipDir() {
        FakeDir rel = new FakeDir("rel", Kind.ZIP, new FakeDir("/MP3", null, null));
        assertSame(rel, ReleaseRules.locate(rel));
    }

    @Test
    void locateSubdirUsesParentSfv() {
        FakeDir rel = new FakeDir("rel", Kind.SFV, new FakeDir("/MP3", null, null));
        FakeDir cd1 = new FakeDir("CD1", null, rel);
        assertSame(rel, ReleaseRules.locate(cd1));
    }

    @Test
    void locateSubdirWithOwnSfvWins() {
        FakeDir rel = new FakeDir("rel", Kind.SFV, null);
        FakeDir cd1 = new FakeDir("CD1", Kind.SFV, rel);
        assertSame(cd1, ReleaseRules.locate(cd1));
    }

    @Test
    void locateNoReleaseFound() {
        FakeDir root = new FakeDir("/", null, null);
        FakeDir section = new FakeDir("/MP3", null, root);
        assertNull(ReleaseRules.locate(section));
    }

    @Test
    void locateGivesUpAfterMaxLevels() {
        FakeDir rel = new FakeDir("rel", Kind.SFV, null);
        FakeDir a = new FakeDir("a", null, rel);
        FakeDir b = new FakeDir("b", null, a);
        FakeDir c = new FakeDir("c", null, b);
        // c, b, a are looked at (3 levels), rel is the 4th; from b it is the 3rd
        assertNull(ReleaseRules.locate(c));
        assertSame(rel, ReleaseRules.locate(b));
    }

    @Test
    void ruleLineMatchesReleaseSubdirsAndExemptsGroups() {
        // 'downloadinc /MP3/* !*': the rule applies below /MP3 and nobody is exempt
        GlobPathPermission rule = new GlobPathPermission("/MP3/*",
                Permission.makeUsers(new StringTokenizer("!*")));
        assertTrue(rule.checkPath(new DirectoryHandle("/MP3/Rel-GRP")));
        assertTrue(rule.checkPath(new DirectoryHandle("/MP3/Rel-GRP/CD1")));
        assertFalse(rule.checkPath(new DirectoryHandle("/TV/Rel-GRP")));
        assertFalse(rule.check(null));

        // an exemption line: '%' stands for the users matched by the line (null user = unauthenticated)
        GlobPathPermission exempt = new GlobPathPermission("/MP3/*",
                Permission.makeUsers(new StringTokenizer("%")));
        assertTrue(exempt.check(null));
    }
}
