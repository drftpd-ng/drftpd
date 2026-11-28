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
package org.drftpd.master.cron;

import org.junit.jupiter.api.Test;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SafeTimerTaskTest {

    @Test
    public void throwingSafeTaskDoesNotKillTheTimer() throws Exception {
        Timer timer = new Timer("SafeTimerTaskTest", true);
        try {
            CountDownLatch thrown = new CountDownLatch(1);
            CountDownLatch second = new CountDownLatch(1);
            timer.schedule(new SafeTimerTask() {
                @Override
                protected void safeRun() {
                    thrown.countDown();
                    throw new IllegalStateException("boom");
                }
            }, 0);
            assertTrue(thrown.await(5, TimeUnit.SECONDS));
            timer.schedule(new TimerTask() {
                @Override
                public void run() {
                    second.countDown();
                }
            }, 50);
            assertTrue(second.await(5, TimeUnit.SECONDS), "timer must still run tasks after a task threw");
        } finally {
            timer.cancel();
        }
    }

    @Test
    public void throwingErrorIsAlsoContainedAndTaskKeepsRepeating() throws Exception {
        Timer timer = new Timer("SafeTimerTaskTest2", true);
        try {
            CountDownLatch runs = new CountDownLatch(3);
            timer.schedule(new SafeTimerTask() {
                @Override
                protected void safeRun() {
                    runs.countDown();
                    throw new AssertionError("boom");
                }
            }, 0, 20);
            assertTrue(runs.await(5, TimeUnit.SECONDS), "a failing periodic task must keep being scheduled");
        } finally {
            timer.cancel();
        }
    }
}
