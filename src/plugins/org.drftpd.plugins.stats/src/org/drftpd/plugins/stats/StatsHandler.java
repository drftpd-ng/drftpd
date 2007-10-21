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

import java.util.ArrayList;
import java.util.Collection;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.apache.oro.text.regex.MalformedPatternException;
import org.drftpd.GlobalContext;
import org.drftpd.config.ConfigHandler;
import org.drftpd.dynamicdata.Key;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.permissions.Permission;
import org.drftpd.permissions.RatioPathPermission;

/**
 * Handles 'creditcheck' and 'creditloss' lines from perms.conf
 * @author fr0w
 * @version $Id$
 */
public class StatsHandler extends ConfigHandler {
	private static final Logger logger = Logger.getLogger(StatsHandler.class);
	
	@SuppressWarnings("unchecked")
	private void handleRatioPathPerm(Key key, StringTokenizer st) {	
		ConfigInterface cfg = GlobalContext.getConfig();
		
		ArrayList<RatioPathPermission> list = (ArrayList<RatioPathPermission>) cfg.getKeyedMap().getObject(key, null);
		
		if (list == null) {
			list = new ArrayList<RatioPathPermission>();
			cfg.getKeyedMap().setObject(key, list);
		}
		
		RatioPathPermission perm = null;
		
		String path = "";
		float ratio = 0F;
		Collection<String> coll = null;
		
		try {
			path = st.nextToken();
			ratio = Float.parseFloat(st.nextToken());
			coll = Permission.makeUsers(st);
			perm = new RatioPathPermission(path, ratio, coll);
			
			list.add(perm);
		} catch (NumberFormatException e) {
			logger.error("Unable to handle '"+key.getKey()+" "+path+" "+ratio+" "+coll.toString(), e);
		} catch (MalformedPatternException e) {
			logger.error("Unable to handle '"+key.getKey()+" "+path+" "+ratio+" "+coll.toString(), e);
		}
	}
	
	public void handleCreditCheck(String directive, StringTokenizer st) {
		handleRatioPathPerm(StatsManager.CREDITCHECK, st);
	}
	
	public void handleCreditLoss(String directive, StringTokenizer st) {
		handleRatioPathPerm(StatsManager.CREDITLOSS, st);
	}

}
