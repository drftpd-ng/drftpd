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
package org.drftpd.plugins.stats;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.config.ConfigHandler;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.permissions.CreditLimitPathPermission;
import org.drftpd.permissions.Permission;
import org.drftpd.permissions.RatioPathPermission;

import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

/**
 * Handles 'creditcheck' and 'creditloss' lines from perms.conf
 * @author fr0w
 * @version $Id$
 */
public class StatsHandler extends ConfigHandler {
	private static final Logger logger = LogManager.getLogger(StatsHandler.class);

	protected static final int DIRECTION_UP = 0;
	protected static final int DIRECTION_DN = 1;
	protected static final String PERIOD_ALL = "ALL";
	protected static final String PERIOD_DAY = "DAY";
	protected static final String PERIOD_WEEK = "WEEK";
	protected static final String PERIOD_MONTH = "MONTH";
	
	private void handleRatioPathPerm(Key<ArrayList<RatioPathPermission>> key, StringTokenizer st) {	
		ConfigInterface cfg = GlobalContext.getConfig();
		
		ArrayList<RatioPathPermission> list = cfg.getKeyedMap().getObject(key, null);
		
		if (list == null) {
			list = new ArrayList<>();
			cfg.getKeyedMap().setObject(key, list);
		}
		
		RatioPathPermission perm;
		
		String path = "";
		float ratio = 0F;
		Collection<String> coll = null;
		
		try {
			path = st.nextToken();
			ratio = Float.parseFloat(st.nextToken());
			coll = Permission.makeUsers(st);
			perm = new RatioPathPermission(path, ratio, coll);
			
			list.add(perm);
		} catch (NumberFormatException|PatternSyntaxException e) {
            logger.error("Unable to handle '{} {} {} {}", key.getKey(), path, ratio, coll != null ? coll.toString() : "", e);
		}
	}

	private void handleCreditLimitPathPerm(Key<ArrayList<CreditLimitPathPermission>> key, StringTokenizer st) {
		ConfigInterface cfg = GlobalContext.getConfig();

		ArrayList<CreditLimitPathPermission> list = cfg.getKeyedMap().getObject(key, null);

		if (list == null) {
			list = new ArrayList<>();
			cfg.getKeyedMap().setObject(key, list);
		}

		CreditLimitPathPermission perm;

		String path = "";
		String dString = "";
		String pString = "";
		String bString = "";
		int direction;
		String period = null;
		long bytes;
		Collection<String> coll = null;

		try {
			path = st.nextToken();
			dString = st.nextToken();
			pString = st.nextToken();
			bString = st.nextToken();

			switch (dString.toUpperCase()) {
				case "UP":
					direction = DIRECTION_UP;
					break;
				case "DN":
					direction = DIRECTION_DN;
					break;
				default:
					throw new NumberFormatException("direction value incorrect");
			}

			switch (pString.toUpperCase()) {
				case PERIOD_ALL:
					period = PERIOD_ALL;
					break;
				case PERIOD_DAY:
					period = PERIOD_DAY;
					break;
				case PERIOD_WEEK:
					period = PERIOD_WEEK;
					break;
				case PERIOD_MONTH:
					period = PERIOD_MONTH;
					break;
				default:
					throw new NumberFormatException("period value incorrect");
			}

			bytes = Bytes.parseBytes(bString);

			coll = Permission.makeUsers(st);
			perm = new CreditLimitPathPermission(path, direction, period, bytes, coll);

			list.add(perm);
		} catch (NumberFormatException|PatternSyntaxException e) {
            logger.error("Unable to handle '{} {} {} {} {} {}", key.getKey(), path, dString, pString, bString, coll != null ? coll.toString() : "", e);
		}
	}
	
	public void handleCreditCheck(String directive, StringTokenizer st) {
		handleRatioPathPerm(StatsManager.CREDITCHECK, st);
	}
	
	public void handleCreditLoss(String directive, StringTokenizer st) {
		handleRatioPathPerm(StatsManager.CREDITLOSS, st);
	}

	public void handleCreditLimit(String directive, StringTokenizer st) {
		handleCreditLimitPathPerm(StatsManager.CREDITLIMIT, st);
	}

}
