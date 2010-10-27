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
package org.drftpd.plugins.sitebot.plugins.dailystats;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.Properties;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.event.ReloadEvent;
import org.drftpd.plugins.sitebot.plugins.dailystats.event.StatsEvent;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserResetHookInterface;
import org.drftpd.usermanager.util.UserComparator;

/**
 * @author djb61
 * @author cyber
 * @version $Id$
 */
public class DailyStats implements UserResetHookInterface {

	private static Logger logger = Logger.getLogger(DailyStats.class);

	private boolean _showzero = false;
	private boolean _dayup = false;
	private boolean _daydn = false;
	private boolean _mndn = false;
	private boolean _mnup = false;
	private boolean _wkdn = false;
	private boolean _wkup = false;
	private int _outputnum;
	private String[] _exempt;

	public void init() {
		logger.info("Starting daily stats plugin");
		loadConf();
		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	private void loadConf() {
		Properties cfg = GlobalContext.getGlobalContext().getPluginsConfig()
		.getPropertiesForPlugin("dailystats.conf");
		if (cfg == null) {
			logger.fatal("conf/plugins/dailystats.conf not found");
			return;
		}

		_dayup = cfg.getProperty("dayup","false").equalsIgnoreCase("true");
		_daydn = cfg.getProperty("daydn","false").equalsIgnoreCase("true");
		_mnup = cfg.getProperty("mnup","false").equalsIgnoreCase("true");
		_mndn = cfg.getProperty("mndn","false").equalsIgnoreCase("true");
		_wkup = cfg.getProperty("wkup","false").equalsIgnoreCase("true");
		_wkdn = cfg.getProperty("wkdn","false").equalsIgnoreCase("true");
		_showzero = cfg.getProperty("showzero","false").equalsIgnoreCase("true");
		_outputnum = Integer.parseInt(cfg.getProperty("outputnum","5"));
		_exempt = cfg.getProperty("exempt", "").toLowerCase().split(" ");
	}

	private Collection<UserStats> getStats(String type) {
		boolean allow;
		String name;
		String files = null;
		String bytes = null;
		ArrayList<User> initialUsers = new ArrayList<User>(GlobalContext.getGlobalContext().getUserManager().getAllUsers());
		ArrayList<UserStats> outputUsers = new ArrayList<UserStats>();

		for (Iterator<User> iter = initialUsers.iterator(); iter.hasNext();) {
			User user = iter.next();
			allow = true;
			for (int i = 0; i < _exempt.length; i++) {
				if (user.isMemberOf(_exempt[i]))
					allow = false;
			}
			if (user.isDeleted()) {
				allow = false;
			}
			if (!allow) {
				iter.remove();
			}
		}

		Collections.sort(initialUsers, new UserComparator(type));

		for (int i=0; ((i < _outputnum) && (i < initialUsers.size())); ++i) {
			if (type.equals("dayup")) {
				if ((initialUsers.get(i).getUploadedBytesDay() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesDay());
				files = String.valueOf(initialUsers.get(i).getUploadedFilesDay());
			} else if (type.equals("daydn")) {
				if ((initialUsers.get(i).getDownloadedBytesDay() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesDay());
				files = String.valueOf(initialUsers.get(i).getDownloadedFilesDay());
			} else if (type.equals("wkup")) {
				if ((initialUsers.get(i).getUploadedBytesWeek() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesWeek());
				files = String.valueOf(initialUsers.get(i).getUploadedFilesWeek());
			} else if (type.equals("wkdn")) {
				if ((initialUsers.get(i).getDownloadedBytesWeek() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesWeek());
				files = String.valueOf(initialUsers.get(i).getDownloadedFilesWeek());
			} else if (type.equals("monthup")) {
				if ((initialUsers.get(i).getUploadedBytesMonth() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getUploadedBytesMonth());
				files = String.valueOf(initialUsers.get(i).getUploadedFilesMonth());
			} else if (type.equals("monthdn")) {
				if ((initialUsers.get(i).getDownloadedBytesMonth() < 1) && (!_showzero))
					continue;
				bytes = Bytes.formatBytes(initialUsers.get(i).getDownloadedBytesMonth());
				files = String.valueOf(initialUsers.get(i).getDownloadedFilesMonth());
			}
			name = initialUsers.get(i).getName();
			outputUsers.add(new UserStats(name, files, bytes));
		}

		return outputUsers;
	}

	public void resetDay(Date d) {
		if (_dayup) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("dayup",getStats("dayup")));
		}
		if (_daydn) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("daydn",getStats("daydn")));
		}
	}

	public void resetWeek(Date d) {
		if (_wkup) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("wkup",getStats("wkup")));
		}
		if (_wkdn) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("wkdn",getStats("wkdn")));
		}
		// Uncomment this if you want day stats at the end of the week also
		// resetDay(d)
	}

	public void resetMonth(Date d) {
		if (_mnup) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("monthup",getStats("monthup")));
		}
		if (_mndn) {
			GlobalContext.getEventService().publishAsync(new StatsEvent("monthdn",getStats("monthdn")));
		}
		// Uncomment this if you want day stats at the end of the month also.
		// If you want week stats when the end of a week coincides with the end of a month
		// then you'll have to do some work on the date passed to check this and then
		// conditionally call resetWeek() based on the result
		// resetDay(d);
	}

	public void resetYear(Date d) {
		// We don't do year stats so do the usual monthstats instead
		resetMonth(d);
	}

	public void resetHour(Date d) {
		// No need for this interval
		// Uncomment the rest to test output
		/*resetDay(d);
		resetWeek(d);
		resetMonth(d);*/
	}

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		loadConf();
	}
}
