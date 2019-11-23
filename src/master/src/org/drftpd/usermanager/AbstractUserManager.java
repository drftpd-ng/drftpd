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
package org.drftpd.usermanager;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.io.PermissionDeniedException;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.MasterPluginUtils;

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.*;

/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id$
 */
public abstract class AbstractUserManager implements UserManager {
	private static final Logger logger = LogManager.getLogger(AbstractUserManager.class);

	protected HashMap<String, SoftReference<User>> _users;

	private ArrayList<UserResetHookInterface> _preResetHooks = new ArrayList<>();

	private ArrayList<UserResetHookInterface> _postResetHooks = new ArrayList<>();

	public void init() throws UserFileException {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadResetHooks();
	}

	protected abstract File getUserpathFile();

	protected void createSiteopUser() {
		User user = createUser("drftpd");
		user.setGroup("drftpd");
		user.setPassword("drftpd");
		user.getKeyedMap().setObject(UserManagement.RATIO, (float) 0);
		user.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMUP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMDN, 0);
		// user.getKeyedMap().setObject(Statistics.LOGINS,0);
		user.getKeyedMap().setObject(UserManagement.CREATED, new Date());
		user.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
		user.getKeyedMap().setObject(UserManagement.WKLY_ALLOTMENT, 0L);
		user.getKeyedMap().setObject(UserManagement.COMMENT, "Auto-Generated");
		user.getKeyedMap().setObject(UserManagement.IRCIDENT, "");
		user.getKeyedMap().setObject(UserManagement.TAGLINE, "drftpd");
		user.getKeyedMap().setObject(UserManagement.BAN_TIME, new Date());
		// user.getKeyedMap().setObject(Nuke.NUKED,0);
		// user.getKeyedMap().setObject(Nuke.NUKEDBYTES,new Long(0));

		try {
			user.addIPMask("*@127.0.0.1");
			user.addIPMask("*@0:0:0:0:0:0:0:1");
		} catch (DuplicateElementException e) {
		}

		try {
			user.addSecondaryGroup("siteop");
		} catch (DuplicateElementException e1) {
		}

		user.commit();
	}

	public User create(String username) throws UserFileException {
		try {
			getUserByName(username);
			// bad, .json file already exists.
			throw new FileExistsException("User " + username + " already exists");
		} catch (IOException e) {
			// bad, some I/O error ocurred.
			throw new UserFileException(e);
		} catch (NoSuchUserException e) {
			// good, no such user was found. create it!
		}

		User user = createUser(username);
		user.commit();

		return user;
	}

	protected abstract User createUser(String username);

	/**
	 * final for now to remove duplicate implementations
	 */
	public synchronized void delete(String username) {
		if (!getUserFile(username).delete())
			throw new RuntimeException(new PermissionDeniedException());
		_users.remove(username);
	}

	protected abstract File getUserFile(String username);

	public Collection<String> getAllGroups() {
		Collection<User> users = getAllUsers();
		ArrayList<String> ret = new ArrayList<>();

		for (User myUser : users) {
			Collection<String> myGroups = myUser.getGroups();

			for (String myGroup : myGroups) {

				if (!ret.contains(myGroup)) {
					ret.add(myGroup);
				}
			}

			if (!ret.contains(myUser.getGroup())) {
				ret.add(myUser.getGroup());
			}
		}

		return ret;
	}

	/**
	 * Get all user names in the system.
	 */
	public abstract Collection<User> getAllUsers();

	public Collection<User> getAllUsersByGroup(String group) {
		Collection<User> c = new ArrayList<>();

		for (User user : getAllUsers()) {

			if (user.isMemberOf(group)) {
				c.add(user);
			}
		}

		return c;
	}

	public User getUserByNameIncludeDeleted(String username) throws NoSuchUserException, UserFileException {
		User user = getUserByNameUnchecked(username);
		return user;
	}

	public User getUserByName(String username) throws NoSuchUserException, UserFileException {
		User user = getUserByNameIncludeDeleted(username);

		if (user.isDeleted()) {
			throw new NoSuchUserException(user.getName() + " is deleted");
		}

		return user;
	}

	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	/**
	 * Return a user given their ident (xxx!xxx@xxx) for a given botname This uses a
	 * regular expression to match the IRCIDENT user property
	 * 
	 * @param ident   ident for user to match
	 * @param botName the name of the IRC bot
	 * @return the user, if found
	 * @throws NoSuchUserException if the user wasn't found
	 */

	public User getUserByIdent(String ident, String botName) throws NoSuchUserException {
		for (User user : getAllUsers()) {
			try {
				String uidentList = user.getKeyedMap().getObject(UserManagement.IRCIDENT);
				String[] identArray = uidentList.split(",");
				for (String anIdentArray : identArray) {
					if (anIdentArray.matches("^" + botName + "|" + ident + "$")) {
						return user;
					}
				}
			} catch (KeyNotFoundException e1) {
			}
		}
		throw new NoSuchUserException("No user found with ident = " + ident);
	}

	public abstract User getUserByNameUnchecked(String username) throws NoSuchUserException, UserFileException;

	protected synchronized void rename(User oldUser, String newUsername) throws UserExistsException, UserFileException {
		if (!_users.containsKey(newUsername)) {
			try {
				getUserByNameUnchecked(newUsername);
			} catch (NoSuchUserException e) {
				_users.remove(oldUser.getName());
				_users.put(newUsername, new SoftReference<>(oldUser));
				return;
			}
		}

		throw new UserExistsException("user " + newUsername + " exists");
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.drftpd.master.cron.TimeEventInterface#resetDay(java.util.Date)
	 */
	public void resetDay(Date d) {
		// Run pre reset hooks
		for (UserResetHookInterface preHook : _preResetHooks) {
			preHook.resetDay(d);
		}
		for (User user : getAllUsers()) {
			user.resetDay(d);
			user.commit();
		}
		// Run post reset hooks
		for (UserResetHookInterface postHook : _postResetHooks) {
			postHook.resetDay(d);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.drftpd.master.cron.TimeEventInterface#resetHour(java.util.Date)
	 */
	public void resetHour(Date d) {
		// Run pre reset hooks
		for (UserResetHookInterface preHook : _preResetHooks) {
			preHook.resetHour(d);
		}
		for (User user : getAllUsers()) {
			user.resetHour(d);
			user.commit();
		}
		// Run post reset hooks
		for (UserResetHookInterface postHook : _postResetHooks) {
			postHook.resetHour(d);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.drftpd.master.cron.TimeEventInterface#resetMonth(java.util.Date)
	 */
	public void resetMonth(Date d) {
		// Run pre reset hooks
		for (UserResetHookInterface preHook : _preResetHooks) {
			preHook.resetMonth(d);
		}
		for (User user : getAllUsers()) {
			user.resetMonth(d);
			user.commit();
		}
		// Run post reset hooks
		for (UserResetHookInterface postHook : _postResetHooks) {
			postHook.resetMonth(d);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.drftpd.master.cron.TimeEventInterface#resetWeek(java.util.Date)
	 */
	public void resetWeek(Date d) {
		// Run pre reset hooks
		for (UserResetHookInterface preHook : _preResetHooks) {
			preHook.resetWeek(d);
		}
		for (User user : getAllUsers()) {
			user.resetWeek(d);
			user.commit();
		}
		// Run post reset hooks
		for (UserResetHookInterface postHook : _postResetHooks) {
			postHook.resetWeek(d);
		}

	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see org.drftpd.master.cron.TimeEventInterface#resetYear(java.util.Date)
	 */
	public void resetYear(Date d) {
		// Run pre reset hooks
		for (UserResetHookInterface preHook : _preResetHooks) {
			preHook.resetYear(d);
		}
		for (User user : getAllUsers()) {
			user.resetYear(d);
			user.commit();
		}
		// Run post reset hooks
		for (UserResetHookInterface postHook : _postResetHooks) {
			postHook.resetYear(d);
		}
	}

	private void loadResetHooks() {
		// Load hooks to be run before the reset
		try {
			List<UserResetHookInterface> preResetHooks = CommonPluginUtils.getPluginObjects(this, "master",
					"PreUserResetHook", "Class");
			for (UserResetHookInterface preResetHook : preResetHooks) {
				preResetHook.init();
				_preResetHooks.add(preResetHook);
                logger.debug("Loading PreUserResetHook into UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(preResetHook));
			}
		} catch (IllegalArgumentException e) {
			logger.error(
					"Failed to load plugins for master extension point 'PreUserResetHook', possibly the master extension "
							+ "point definition has changed in the plugin.xml",
					e);
		}

		// Load hooks to be run after the reset
		try {
			List<UserResetHookInterface> postResetHooks = CommonPluginUtils.getPluginObjects(this, "master",
					"PostUserResetHook", "Class");
			for (UserResetHookInterface postResetHook : postResetHooks) {
				postResetHook.init();
				_postResetHooks.add(postResetHook);
                logger.debug("Loading PostUserResetHook into UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(postResetHook));
			}
		} catch (IllegalArgumentException e) {
			logger.error(
					"Failed to load plugins for master extension point 'PostUserResetHook', possibly the master extension "
							+ "point definition has changed in the plugin.xml",
					e);
		}
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(UnloadPluginEvent event) {
		// Unload any pre reset hooks that belong to the unloaded plugin
		Set<UserResetHookInterface> unloadedPreResetHooks = MasterPluginUtils.getUnloadedExtensionObjects(this,
				"PreUserResetHook", event, _preResetHooks);
		if (!unloadedPreResetHooks.isEmpty()) {
			ArrayList<UserResetHookInterface> clonedPreResetHooks = new ArrayList<>(_preResetHooks);
			boolean hookRemoved = false;
			for (Iterator<UserResetHookInterface> iter = clonedPreResetHooks.iterator(); iter.hasNext();) {
				UserResetHookInterface preResetHook = iter.next();
				if (unloadedPreResetHooks.contains(preResetHook)) {
                    logger.debug("Unloading PreUserResetHook from UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(preResetHook));
					iter.remove();
					hookRemoved = true;
				}
			}
			if (hookRemoved) {
				_preResetHooks = clonedPreResetHooks;
			}
		}

		// Unload any post reset hooks that belong to the unloaded plugin
		Set<UserResetHookInterface> unloadedPostResetHooks = MasterPluginUtils.getUnloadedExtensionObjects(this,
				"PostUserResetHook", event, _postResetHooks);
		if (!unloadedPostResetHooks.isEmpty()) {
			ArrayList<UserResetHookInterface> clonedPostResetHooks = new ArrayList<>(_postResetHooks);
			boolean hookRemoved = false;
			for (Iterator<UserResetHookInterface> iter = clonedPostResetHooks.iterator(); iter.hasNext();) {
				UserResetHookInterface postResetHook = iter.next();
				if (unloadedPostResetHooks.contains(postResetHook)) {
                    logger.debug("Unloading PostUserResetHook from UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(postResetHook));
					iter.remove();
					hookRemoved = true;
				}
			}
			if (hookRemoved) {
				_postResetHooks = clonedPostResetHooks;
			}
		}
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(LoadPluginEvent event) {
		// Load any new pre reset hooks provided by the plugin being loaded
		try {
			List<UserResetHookInterface> loadedPreResetHooks = MasterPluginUtils.getLoadedExtensionObjects(this,
					"master", "PreUserResetHook", "Class", event);
			if (!loadedPreResetHooks.isEmpty()) {
				ArrayList<UserResetHookInterface> clonedPreResetHooks = new ArrayList<>(_preResetHooks);
				for (UserResetHookInterface preResetHook : loadedPreResetHooks) {
					preResetHook.init();
					clonedPreResetHooks.add(preResetHook);
                    logger.debug("Loading PreUserResetHook into UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(preResetHook));
				}
				_preResetHooks = clonedPreResetHooks;
			}
		} catch (IllegalArgumentException e) {
			logger.error(
					"Failed to load plugins from a loadplugin event for master extension point 'PreUserResetHook', possibly the "
							+ "master extension point definition has changed in the plugin.xml",
					e);
		}

		// Load any new post reset hooks provided by the plugin being loaded
		try {
			List<UserResetHookInterface> loadedPostResetHooks = MasterPluginUtils.getLoadedExtensionObjects(this,
					"master", "PostUserResetHook", "Class", event);
			if (!loadedPostResetHooks.isEmpty()) {
				ArrayList<UserResetHookInterface> clonedPostResetHooks = new ArrayList<>(_postResetHooks);
				for (UserResetHookInterface postResetHook : loadedPostResetHooks) {
					postResetHook.init();
					clonedPostResetHooks.add(postResetHook);
                    logger.debug("Loading PostUserResetHook into UserManager from plugin {}", CommonPluginUtils.getPluginIdForObject(postResetHook));
				}
				_postResetHooks = clonedPostResetHooks;
			}
		} catch (IllegalArgumentException e) {
			logger.error(
					"Failed to load plugins from a loadplugin event for master extension point 'PostUserResetHook', possibly the "
							+ "master extension point definition has changed in the plugin.xml",
					e);
		}
	}
}
