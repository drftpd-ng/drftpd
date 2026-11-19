package org.drftpd.statistics.master;

import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.permissions.stats.RatioPathPermission;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.drftpd.master.vfs.DirectoryHandle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.StringTokenizer;

import static org.junit.jupiter.api.Assertions.*;

public class CreditLossRatioTest {

    /** The perms.conf line from #255 (user variant; the =group variant needs a user manager): "creditloss /TEST/* 4 -testuser" */
    private static List<RatioPathPermission> issue255Perms() {
        return List.of(new RatioPathPermission("/TEST/*", 4f,
                Permission.makeUsers(new StringTokenizer("-testuser"))));
    }

    private static BeanUser userWithRatio(float ratio) {
        BeanUser user = new BeanUser("testuser");
        user.getKeyedMap().setObject(UserManagement.RATIO, ratio);
        return user;
    }

    @Test
    public void ratioZeroIsLeech() {
        assertTrue(StatsManager.isLeech(0f));
        assertFalse(StatsManager.isLeech(1f));
        assertFalse(StatsManager.isLeech(3f));
        assertFalse(StatsManager.isLeech(0.5f));
    }

    @Test
    public void leechUserIsNotChargedByMatchingCreditlossLine() {
        // #255: ratio 0 user downloading from /TEST with a matching creditloss line was charged 4x
        // the file size and denied with "Not enough credits"
        float ratio = StatsManager.creditLossRatio(0f, issue255Perms(),
                new DirectoryHandle("/TEST/Some.Release"), userWithRatio(0f));

        assertEquals(0f, ratio);
    }

    @Test
    public void normalUserIsStillChargedByMatchingCreditlossLine() {
        float ratio = StatsManager.creditLossRatio(3f, issue255Perms(),
                new DirectoryHandle("/TEST/Some.Release"), userWithRatio(3f));

        assertEquals(4f, ratio);
    }

    @Test
    public void creditlossLineOnlyAppliesToItsPath() {
        assertEquals(1f, StatsManager.creditLossRatio(3f, issue255Perms(),
                new DirectoryHandle("/OTHER/Some.Release"), userWithRatio(3f)));
        assertEquals(0f, StatsManager.creditLossRatio(0f, issue255Perms(),
                new DirectoryHandle("/OTHER/Some.Release"), userWithRatio(0f)));
    }

    @Test
    public void noCreditlossLinesMeansDefaults() {
        assertEquals(1f, StatsManager.creditLossRatio(3f, null,
                new DirectoryHandle("/TEST/x"), userWithRatio(3f)));
        assertEquals(0f, StatsManager.creditLossRatio(0f, null,
                new DirectoryHandle("/TEST/x"), userWithRatio(0f)));
    }
}
