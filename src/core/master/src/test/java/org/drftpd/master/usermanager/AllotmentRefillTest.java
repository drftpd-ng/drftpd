/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.master.usermanager;

import org.drftpd.master.commands.usermanagement.UserManagement;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

public class AllotmentRefillTest {

    @Test
    public void positiveAllotmentReplacesCredits() {
        assertEquals(5000L, AbstractUser.creditsAfterAllotment(123L, 5000L));
        // replaced, not added to, even when the user has more than the allotment
        assertEquals(5000L, AbstractUser.creditsAfterAllotment(9_000_000L, 5000L));
        assertEquals(5000L, AbstractUser.creditsAfterAllotment(-400L, 5000L));
    }

    @Test
    public void zeroAllotmentKeepsCredits() {
        assertEquals(123L, AbstractUser.creditsAfterAllotment(123L, 0L));
        assertEquals(0L, AbstractUser.creditsAfterAllotment(0L, 0L));
        assertEquals(-7L, AbstractUser.creditsAfterAllotment(-7L, 0L));
    }

    @Test
    public void negativeAllotmentIsTreatedAsOff() {
        assertEquals(123L, AbstractUser.creditsAfterAllotment(123L, -1L));
    }

    @Test
    public void monthlyKeyIsSeparateFromWeekly() {
        assertEquals("monthly_allotment", UserManagement.MONTHLYALLOTMENT.getKey());
        assertNotEquals(UserManagement.WKLYALLOTMENT, UserManagement.MONTHLYALLOTMENT);
    }
}
