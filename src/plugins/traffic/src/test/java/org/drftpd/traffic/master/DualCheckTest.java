package org.drftpd.traffic.master;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DualCheckTest {
    private static final long MIN = 500 * 1000;
    private static final long FAST = 800 * 1000;
    private static final long OLD = 20_000;

    private static DualCheck.Sample s(String user, String release, boolean stor, long speed, long elapsed) {
        return new DualCheck.Sample(user, release, stor, speed, elapsed, user + release + elapsed);
    }

    @Test
    void sameUserSameReleaseTwoTransfersIsDetected() {
        DualCheck.Sample older = s("bob", "/MP3/Rel", true, FAST, OLD);
        DualCheck.Sample newer = s("bob", "/MP3/Rel", true, FAST, 5000);
        List<DualCheck.Violation> v = DualCheck.detect(List.of(older, newer), MIN, 2, true, false, null);
        assertEquals(1, v.size());
        assertEquals("bob", v.get(0).getUser());
        assertEquals(2, v.get(0).getSamples().size());
        assertSame(newer, v.get(0).getTarget(), "the most recent transfer is the target");
    }

    @Test
    void differentReleasesAreNotDetected() {
        List<DualCheck.Violation> v = DualCheck.detect(List.of(
                s("bob", "/MP3/RelA", true, FAST, OLD), s("bob", "/MP3/RelB", true, FAST, OLD)),
                MIN, 2, true, true, null);
        assertTrue(v.isEmpty());
    }

    @Test
    void differentUsersSameReleaseAreNotDetected() {
        assertTrue(DualCheck.detect(List.of(
                s("bob", "/MP3/Rel", true, FAST, OLD), s("alice", "/MP3/Rel", true, FAST, OLD)),
                MIN, 2, true, true, null).isEmpty());
    }

    @Test
    void belowMinSpeedIsNotDetected() {
        assertTrue(DualCheck.detect(List.of(
                s("bob", "/MP3/Rel", true, FAST, OLD), s("bob", "/MP3/Rel", true, MIN - 1, OLD)),
                MIN, 2, true, true, null).isEmpty());
    }

    @Test
    void tooYoungTransfersAreIgnored() {
        assertTrue(DualCheck.detect(List.of(
                s("bob", "/MP3/Rel", true, FAST, OLD), s("bob", "/MP3/Rel", true, FAST, 100)),
                MIN, 2, true, true, null).isEmpty());
    }

    @Test
    void exemptTransfersAreIgnored() {
        List<DualCheck.Sample> l = List.of(s("bob", "/MP3/Rel", true, FAST, OLD), s("bob", "/MP3/Rel", true, FAST, OLD + 1));
        assertTrue(DualCheck.detect(l, MIN, 2, true, true, x -> x.getUser().equals("bob")).isEmpty());
        assertEquals(1, DualCheck.detect(l, MIN, 2, true, true, x -> x.getUser().equals("alice")).size());
    }

    @Test
    void directionsAreCountedSeparately() {
        List<DualCheck.Sample> mixed = List.of(s("bob", "/MP3/Rel", true, FAST, OLD), s("bob", "/MP3/Rel", false, FAST, OLD));
        assertTrue(DualCheck.detect(mixed, MIN, 2, true, true, null).isEmpty());
        List<DualCheck.Sample> downs = List.of(s("bob", "/MP3/Rel", false, FAST, OLD), s("bob", "/MP3/Rel", false, FAST, OLD + 1));
        assertTrue(DualCheck.detect(downs, MIN, 2, true, false, null).isEmpty(), "downloads ignored when only up is enabled");
        assertEquals(1, DualCheck.detect(downs, MIN, 2, false, true, null).size());
    }

    @Test
    void limitIsRespectedAndNeverBelowTwo() {
        List<DualCheck.Sample> two = List.of(s("bob", "/R", true, FAST, OLD), s("bob", "/R", true, FAST, OLD + 1));
        assertTrue(DualCheck.detect(two, MIN, 3, true, true, null).isEmpty());
        List<DualCheck.Sample> one = List.of(s("bob", "/R", true, FAST, OLD));
        assertTrue(DualCheck.detect(one, MIN, 0, true, true, null).isEmpty(), "a single transfer never triggers");
        assertTrue(DualCheck.detect(one, MIN, 1, true, true, null).isEmpty());
    }

    @Test
    void misconfigurationNeverMatchesEverybody() {
        List<DualCheck.Sample> two = List.of(s("bob", "/R", true, FAST, OLD), s("bob", "/R", true, FAST, OLD + 1));
        assertTrue(DualCheck.detect(two, 0, 2, true, true, null).isEmpty(), "minspeed 0 disables detection");
        assertTrue(DualCheck.detect(two, MIN, 2, false, false, null).isEmpty(), "no direction disables detection");
        assertTrue(DualCheck.detect(null, MIN, 2, true, true, null).isEmpty());
    }
}
