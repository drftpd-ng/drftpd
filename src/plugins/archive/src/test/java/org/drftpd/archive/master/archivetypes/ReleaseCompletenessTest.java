package org.drftpd.archive.master.archivetypes;

import org.junit.jupiter.api.Test;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests for the skipincomplete / skipnuked options (issue #276, item 3).
 */
public class ReleaseCompletenessTest {

    private static class FakeDir implements ReleaseCompleteness.Node {
        private final Boolean _finished;
        private final boolean _fails;
        private final List<FakeDir> _children;

        FakeDir(Boolean finished, FakeDir... children) {
            this(finished, false, children);
        }

        FakeDir(Boolean finished, boolean fails, FakeDir... children) {
            _finished = finished;
            _fails = fails;
            _children = List.of(children);
        }

        @Override
        public Boolean sfvFinished() throws Exception {
            if (_fails) {
                throw new java.io.IOException("slave offline");
            }
            return _finished;
        }

        @Override
        public Collection<? extends ReleaseCompleteness.Node> children() throws FileNotFoundException {
            return _children;
        }
    }

    @Test
    void finishedSfvIsComplete() {
        assertTrue(ReleaseCompleteness.isComplete(new FakeDir(true)));
    }

    @Test
    void unfinishedSfvIsIncomplete() {
        assertFalse(ReleaseCompleteness.isComplete(new FakeDir(false)));
    }

    @Test
    void releaseWithoutSfvIsComplete() {
        assertTrue(ReleaseCompleteness.isComplete(new FakeDir(null)));
        assertTrue(ReleaseCompleteness.isComplete(new FakeDir(null, new FakeDir(null))));
    }

    @Test
    void incompleteSubdirMakesReleaseIncomplete() {
        FakeDir release = new FakeDir(null, new FakeDir(true), new FakeDir(false), new FakeDir(null));
        assertFalse(ReleaseCompleteness.isComplete(release));
    }

    @Test
    void allSubdirsFinishedIsComplete() {
        FakeDir release = new FakeDir(null, new FakeDir(true), new FakeDir(true), new FakeDir(null));
        assertTrue(ReleaseCompleteness.isComplete(release));
    }

    @Test
    void unknownStateIsIncomplete() {
        assertFalse(ReleaseCompleteness.isComplete(new FakeDir(true, true)));
        assertFalse(ReleaseCompleteness.isComplete(new FakeDir(null, new FakeDir(true, true))));
    }

    @Test
    void nothingIsSkippedByDefault() {
        assertFalse(ReleaseCompleteness.shouldSkip(false, false, () -> true, () -> false));
    }

    @Test
    void nukedIsOnlySkippedWhenEnabled() {
        assertTrue(ReleaseCompleteness.shouldSkip(true, false, () -> true, () -> true));
        assertFalse(ReleaseCompleteness.shouldSkip(true, false, () -> false, () -> true));
        assertFalse(ReleaseCompleteness.shouldSkip(false, false, () -> true, () -> true));
    }

    @Test
    void incompleteIsOnlySkippedWhenEnabled() {
        assertTrue(ReleaseCompleteness.shouldSkip(false, true, () -> false, () -> false));
        assertFalse(ReleaseCompleteness.shouldSkip(false, true, () -> false, () -> true));
        assertFalse(ReleaseCompleteness.shouldSkip(false, false, () -> false, () -> false));
    }

    @Test
    void checksAreOnlyEvaluatedWhenEnabled() {
        // the zipscript lookup must not run when skipincomplete is off, nor the nuke lookup when skipnuked is off
        ReleaseCompleteness.shouldSkip(false, false, () -> {
            throw new AssertionError("nuke check ran");
        }, () -> {
            throw new AssertionError("complete check ran");
        });
        // a nuked release does not need the (slow) completeness check
        assertTrue(ReleaseCompleteness.shouldSkip(true, true, () -> true, () -> {
            throw new AssertionError("complete check ran");
        }));
    }

    @Test
    void flagParsing() {
        assertFalse(ReleaseCompleteness.parseFlag(null, false, "1.skipnuked"));
        assertFalse(ReleaseCompleteness.parseFlag("  ", false, "1.skipnuked"));
        assertTrue(ReleaseCompleteness.parseFlag("true", false, "1.skipnuked"));
        assertTrue(ReleaseCompleteness.parseFlag(" TRUE ", false, "1.skipnuked"));
        assertTrue(ReleaseCompleteness.parseFlag("1", false, "1.skipnuked"));
        assertTrue(ReleaseCompleteness.parseFlag("yes", false, "1.skipnuked"));
        assertFalse(ReleaseCompleteness.parseFlag("false", true, "1.skipnuked"));
        assertFalse(ReleaseCompleteness.parseFlag("0", true, "1.skipnuked"));
        assertFalse(ReleaseCompleteness.parseFlag("garbage", false, "1.skipnuked"));
        assertEquals(true, ReleaseCompleteness.parseFlag("garbage", true, "1.skipnuked"));
    }
}
