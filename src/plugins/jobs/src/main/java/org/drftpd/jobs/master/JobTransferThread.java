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
package org.drftpd.jobs.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;


/**
 * @author zubov
 * @version $Id$
 */
public class JobTransferThread extends Thread {
    private static final Logger logger = LogManager.getLogger(JobTransferThread.class);
    private static int count = 1;
    private final JobManager _jm;

    /**
     * This class sends a JobTransfer if it is available
     */
    public JobTransferThread(JobManager jm) {
        super("JobTransferThread - " + count++);
        _jm = jm;
    }

    public void run() {
        try {
            _jm.processJob();
        } catch (Exception e) {
            logger.debug("", e);
        }
    }
}
