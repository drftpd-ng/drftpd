package org.drftpd.master.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Properties;

import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.HostMaskCollection;
import org.drftpd.master.usermanager.Group;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.junit.jupiter.api.Test;

public class SessionTest {

    static class StubUser extends BeanUser {
        public StubUser(String username) {
            super(username);
        }

        @Override
        public Group getGroup() {
            return null;
        }

        @Override
        public List<Group> getGroups() {
            return Collections.emptyList();
        }

        @Override
        public HostMaskCollection getHostMaskCollection() {
            return new HostMaskCollection();
        }
    }

    static class StubSession extends Session {
        @Override
        public boolean isSecure() {
            return false;
        }

        @Override
        public void printOutput(Object o) {
        }

        @Override
        public void printOutput(int code, Object o) {
        }
    }

    @Test
    public void testAverageSpeedDivisionByZero() {
        StubSession session = new StubSession();
        StubUser user = new StubUser("testuser");

        // user.getDownloadedBytes() + user.getUploadedBytes()
        // divided by
        // ((user.getDownloadedTime() + user.getUploadedTime()) / 1000) + 1

        // We want the divisor to be 0.
        // ((dTime + uTime) / 1000) + 1 = 0
        // (dTime + uTime) / 1000 = -1
        // dTime + uTime = -1000

        user.setDownloadedTime(-500);
        user.setUploadedTime(-500);
        user.setDownloadedBytes(1000);
        user.setUploadedBytes(1000);

        // This should NOT throw ArithmeticException when fixed
        assertDoesNotThrow(() -> session.getReplacerEnvironment(new HashMap<>(), user));

        java.util.Map<String, Object> env = session.getReplacerEnvironment(new HashMap<>(), user);
        assertEquals("0B", env.get("averagespeed"));
    }

    @Test
    public void testAverageSpeedWithMoreNegativeTime() {
        // Before clamping, -3000 ms gave a divisor of -2 and a negative average speed
        StubUser user = new StubUser("testuser");
        user.setDownloadedTime(-1000);
        user.setUploadedTime(-2000);
        user.setDownloadedBytes(1000);
        user.setUploadedBytes(1000);

        assertEquals(0L, Session.getAverageSpeed(user));
        assertEquals("0B", new StubSession().getReplacerEnvironment(new HashMap<>(), user).get("averagespeed"));
    }

    @Test
    public void testAverageSpeedWithNegativeBytes() {
        // Values like the userfile in #345: negative upload bytes, positive time
        StubUser user = new StubUser("testuser");
        user.setDownloadedTime(5000);
        user.setUploadedTime(0);
        user.setDownloadedBytes(8847);
        user.setUploadedBytes(-125538012);

        assertEquals(0L, Session.getAverageSpeed(user));
    }

    @Test
    public void testAverageSpeedIsUnchangedForNormalValues() {
        StubUser user = new StubUser("testuser");
        user.setDownloadedTime(3000);
        user.setUploadedTime(1000);
        user.setDownloadedBytes(6000);
        user.setUploadedBytes(4000);

        // Same formula as before the fix: bytes / ((millis / 1000) + 1) = 10000 / 5
        assertEquals(2000L, Session.getAverageSpeed(user));
        assertEquals(Bytes.formatBytes(2000L),
                new StubSession().getReplacerEnvironment(new HashMap<>(), user).get("averagespeed"));
    }
}
