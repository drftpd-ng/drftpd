/*
 * Created on Dec 20, 2004
 *
 * TODO To change the template for this generated file go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
package net.sf.drftpd.master.config;

import java.io.IOException;
import java.util.List;
import java.util.Observer;
import java.util.Properties;

import net.sf.drftpd.util.PortRange;

import org.drftpd.GlobalContext;
import org.drftpd.commands.Reply;
import org.drftpd.permissions.PathPermission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.User;

/**
 * @author mog
 *
 * TODO To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Style - Code Templates
 */
public interface ConfigInterface {
	public abstract boolean checkPathPermission(String key, User fromUser,
			LinkedRemoteFileInterface path);

	public abstract boolean checkPathPermission(String key, User fromUser,
			LinkedRemoteFileInterface path, boolean defaults);

	public abstract boolean checkPermission(String key, User user);

	public abstract void directoryMessage(Reply response, User user,
			LinkedRemoteFileInterface dir);

	/**
	 * @return Returns the bouncerIp.
	 */
	public abstract List getBouncerIps();

	public abstract float getCreditCheckRatio(LinkedRemoteFileInterface path,
			User fromUser);

	public abstract float getCreditLossRatio(LinkedRemoteFileInterface path,
			User fromUser);

	public abstract String getDirName(String name);

	public abstract String getFileName(String name);

	public abstract GlobalContext getGlobalContext();

	public abstract boolean getHideIps();

	public abstract String getLoginPrompt();

	public abstract int getMaxUsersExempt();

	public abstract int getMaxUsersTotal();

	public abstract long getSlaveStatusUpdateTime();

	public abstract Properties getZsConfig();

	public abstract void loadConfig(Properties cfg, GlobalContext gctx)
			throws IOException;

	public abstract void addPathPermission(String key, PathPermission permission);

	/**
	 * Returns true if user is allowed into a shutdown server.
	 */
	public abstract boolean isLoginAllowed(User user);

	public abstract PortRange getPortRange();

	public abstract void addObserver(Observer observer);
}