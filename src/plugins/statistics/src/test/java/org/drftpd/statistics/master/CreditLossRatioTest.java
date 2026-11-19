package org.drftpd.statistics.master;

import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.master.usermanager.javabeans.BeanUser;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class CreditLossRatioTest {

    @Test
    public void ratioZeroIsLeech() {
        assertTrue(StatsManager.isLeech(0f));
        assertFalse(StatsManager.isLeech(1f));
        assertFalse(StatsManager.isLeech(3f));
        assertFalse(StatsManager.isLeech(0.5f));
    }

    @Test
    public void leechUserIsNeverChargedForDownloads() {
        // Returns before looking at perms.conf creditloss lines, so no GlobalContext is needed:
        // before the fix a matching creditloss line overrode the leech user's 0 and every download
        // was denied with "Not enough credits" (#255).
        BeanUser user = new BeanUser("leech");
        user.getKeyedMap().setObject(UserManagement.RATIO, 0f);

        assertEquals(0f, new StatsManager().getCreditLossRatio(null, user));
    }
}
