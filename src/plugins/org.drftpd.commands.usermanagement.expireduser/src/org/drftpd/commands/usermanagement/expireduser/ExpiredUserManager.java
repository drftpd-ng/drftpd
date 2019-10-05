/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands.usermanagement.expireduser;

import java.util.Date;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.drftpd.commands.usermanagement.expireduser.metadata.ExpiredUserData;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserResetHookInterface;

/**
 * @author cyber
 */
public class ExpiredUserManager implements UserResetHookInterface {

	private static Logger logger = LogManager.getLogger(ExpiredUserManager.class);
	
	private boolean _delete;
	private boolean _purge;
	private String _chgrp;
	private String _setgrp;
	
	public void init() {
		logger.debug("Loaded ExpiredUser Plugin");
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("expireduser.conf");
		if (cfg == null) {
			logger.fatal("conf/expireduser.conf not found");
		}

		_delete = cfg.getProperty("delete","false").equalsIgnoreCase("true");
		_purge = cfg.getProperty("purge","false").equalsIgnoreCase("true");
		_chgrp = cfg.getProperty("chgrp","");
		_setgrp = cfg.getProperty("setgrp","");
	}

	private void doExpired(User user) {
		if (_delete) {
			user.setDeleted(true);
		}
		
		if (_purge) {
			user.setDeleted(true);
			user.purge();
			user.commit();
			return;
		}
		
		if (!_chgrp.isEmpty()) {
			String[] groups = _chgrp.split(" ");
			for (String group : groups) {
				user.toggleGroup(group);	
			}
			
		}
		
		if (!_setgrp.isEmpty()) {
			String[] groups = _setgrp.split(" ");
			user.setGroup(groups[0]);
		}		
		user.commit();
	}
	
	
	public void resetDay(Date d) {
		for (User user : GlobalContext.getGlobalContext().getUserManager().getAllUsers()) {
			if (user.getKeyedMap().getObject(ExpiredUserData.EXPIRES,new Date(4102441199000L)).before(new Date())) {
				doExpired(user);
			}
		}
	}

	public void resetWeek(Date d) {
		// Not Needed - Ignore
	}

	public void resetMonth(Date d) {
		// Not Needed - Reset Day
		resetDay(d);
	}

	public void resetYear(Date d) {
		// Not Needed - Reset Day
		resetDay(d);
	}

	public void resetHour(Date d) {
		// Not Needed - Ignore
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
}
