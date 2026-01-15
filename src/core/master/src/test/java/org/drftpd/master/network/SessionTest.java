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
}
