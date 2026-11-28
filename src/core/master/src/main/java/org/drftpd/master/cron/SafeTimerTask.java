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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.TimerTask;

/**
 * A {@link TimerTask} that never lets an exception escape from {@link #run()}.
 * <p>
 * An uncaught exception in a task terminates its {@link java.util.Timer} for good, and the timer returned by
 * GlobalContext#getTimer() is shared by the core and many plugins, so one failing task would silently stop all
 * of them. Subclasses implement {@link #safeRun()}; anything thrown from it is logged and swallowed, and the
 * task keeps being scheduled.
 */
public abstract class SafeTimerTask extends TimerTask {

    private static final Logger logger = LogManager.getLogger(SafeTimerTask.class);

    @Override
    public final void run() {
        try {
            safeRun();
        } catch (Throwable t) {
            logger.error("Unhandled error in timer task {}, the timer is kept alive", getClass().getName(), t);
        }
    }

    /**
     * The work of the task. May throw, the exception is logged by {@link #run()}.
     */
    protected abstract void safeRun();
}
