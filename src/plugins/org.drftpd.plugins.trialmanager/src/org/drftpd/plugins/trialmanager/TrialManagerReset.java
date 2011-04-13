package org.drftpd.plugins.trialmanager;

import java.util.Date;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.usermanager.UserResetHookInterface;

public class TrialManagerReset implements UserResetHookInterface {
	private static final Logger logger = Logger.getLogger(TrialManagerReset.class);
	
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
		if (trialmanager == null) {
			return false;
		}
		return true;
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