/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.master.slavemanagement;

import org.drftpd.master.stats.ExtendedTimedStats;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * TimeManager calls only resetYear() on 1 January and only resetMonth() on the 1st of other months, so a slave
 * must reset the shorter periods in those calls too.
 */
public class RemoteSlaveResetTest {

    private static RemoteSlave slaveWithTraffic() {
        RemoteSlave rs = new RemoteSlave("slave1");
        rs.updateUploadedBytes(100L);
        rs.updateDownloadedBytes(200L);
        return rs;
    }

    @Test
    public void testResetYearClearsMonthAndDay() {
        RemoteSlave rs = slaveWithTraffic();

        rs.resetYear(new Date());

        assertEquals(0L, rs.getUploadedBytesMonth());
        assertEquals(0L, rs.getDownloadedBytesMonth());
        assertEquals(0L, rs.getUploadedBytesDay());
        assertEquals(0L, rs.getDownloadedBytesDay());
        // Totals and the week are not part of a year reset
        assertEquals(100L, rs.getUploadedBytes());
        assertEquals(100L, rs.getUploadedBytesForPeriod(ExtendedTimedStats.P_WEEK));
    }

    @Test
    public void testResetMonthClearsDay() {
        RemoteSlave rs = slaveWithTraffic();

        rs.resetMonth(new Date());

        assertEquals(0L, rs.getUploadedBytesMonth());
        assertEquals(0L, rs.getDownloadedBytesDay());
        assertEquals(200L, rs.getDownloadedBytes());
    }
}
