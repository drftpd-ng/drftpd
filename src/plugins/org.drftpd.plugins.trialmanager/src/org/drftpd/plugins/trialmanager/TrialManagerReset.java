package org.drftpd.plugins.trialmanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.usermanager.UserResetHookInterface;

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
			logger.debug("Could Not Load Trial Manager - ",e);
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