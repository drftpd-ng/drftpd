package net.sf.drftpd.event.listeners;

import java.util.StringTokenizer;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.master.config.Permission;
import net.sf.drftpd.master.usermanager.User;

/**
 * @author mog
 * @version $Id: Limit.java,v 1.2 2004/01/03 23:50:53 mog Exp $
 */
class Limit {
	public Limit() {
	}
	private String _actionFailed;
	private String _actionPassed;
	private long _bytes;
	private String _name;
	private int _period;
	private Permission _perm;

	public void doFailed(User user) {
		Trial.doAction(getActionFailed(), user);
	}

	public void doPassed(User user) {
		Trial.doAction(getActionPassed(), user);
	}

	public String getActionFailed() {
		return _actionFailed;
	}

	public String getActionPassed() {
		return _actionPassed;
	}

	public long getBytes() {
		return _bytes;
	}

	public int getPeriod() {
		return _period;
	}

	public Permission getPerm() {
		return _perm;
	}

	public void setActionFailed(String action) {
		validateAction(action);
		_actionFailed = action;
	}

	public void setActionPassed(String action) {
		validateAction(action);
		_actionPassed = action;
	}

	public void setBytes(long bytes) {
		_bytes = bytes;
	}

	public void setName(String name) {
		_name = name;
	}

	public void setPeriod(int period) {
		_period = period;
	}

	public void setPerm(Permission perm) {
		_perm = perm;
	}

	public String toString() {
		return "Limit[name="
			+ _name
			+ ",bytes="
			+ Bytes.formatBytes(_bytes)
			+ ",period="
			+ Trial.getPeriodName(_period)
			+ "]";
	}

	private void validateAction(String action) {
		if (action == null)
			return;
		//action = action.toLowerCase();
		StringTokenizer st = new StringTokenizer(action);
		if (!st.hasMoreTokens())
			return;
		String cmd = st.nextToken();
		if (!("deluser".equals(action)
			|| "purge".equals(action)
			|| "chgrp".equals(cmd))) {
			throw new IllegalArgumentException();
		}
	}

	public String getName() {
		return _name;
	}

}