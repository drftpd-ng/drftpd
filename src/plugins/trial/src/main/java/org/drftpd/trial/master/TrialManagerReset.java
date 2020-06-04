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
package org.drftpd.trial.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.master.usermanager.UserResetHookInterface;

import java.util.Date;

public class TrialManagerReset implements UserResetHookInterface {
    private static final Logger logger = LogManager.getLogger(TrialManagerReset.class);

    private TrialManager trialmanager;

    public void init() {
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    private boolean getTrialManager() {
        try {
            trialmanager = TrialManager.getTrialManager();
        } catch (RuntimeException e) {
            logger.debug("Could Not Load Trial Manager - ", e);
        }
        return trialmanager != null;
    }

    @Override
    public void resetDay(Date d) {
        if (getTrialManager()) {
            for (TrialType trialtype : trialmanager.getTrials()) {
                if (trialtype.getPeriod() == 3) {
                    trialtype.doTrial();
                }
            }
        }
    }

    @Override
    public void resetWeek(Date d) {
        if (getTrialManager()) {
            for (TrialType trialtype : trialmanager.getTrials()) {
                if (trialtype.getPeriod() == 2) {
                    trialtype.doTrial();
                }
            }
        }
    }

    @Override
    public void resetMonth(Date d) {
        if (getTrialManager()) {
            for (TrialType trialtype : trialmanager.getTrials()) {
                if (trialtype.getPeriod() == 1) {
                    trialtype.doTrial();
                }
            }
        }
        resetDay(d);
    }

    /*
     * Not Needed
     */
    @Override
    public void resetYear(Date d) {
    }

    /*
     * Not Needed
     */
    @Override
    public void resetHour(Date d) {

    }


}