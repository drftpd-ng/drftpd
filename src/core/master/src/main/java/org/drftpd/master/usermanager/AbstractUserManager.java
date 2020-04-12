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
package org.drftpd.master.usermanager;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.usermanagement.GroupManagement;
import org.drftpd.master.commands.usermanagement.UserManagement;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.io.PermissionDeniedException;
import org.drftpd.slave.exceptions.FileExistsException;

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
	protected HashMap<String, SoftReference<Group>> _groups;

	private ArrayList<UserResetHookInterface> _preResetHooks = new ArrayList<>();

	private ArrayList<UserResetHookInterface> _postResetHooks = new ArrayList<>();

	public void init() throws UserFileException, GroupFileException {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadResetHooks();
	}

	protected abstract File getUserpathFile();

	protected void createSiteopUser() {
		Group group = createGroupImpl("drftpd");
		group.getKeyedMap().setObject(GroupManagement.GROUPSLOTS, 0);
		group.getKeyedMap().setObject(GroupManagement.LEECHSLOTS, 0);
		group.commit();
		User user = createUserImpl("drftpd");
		user.setGroup(group);
		user.setPassword("drftpd");
		user.getKeyedMap().setObject(UserManagement.RATIO, (float) 0);
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
		} catch (DuplicateElementException ignored) {
		}

		try {
			Group g = createGroupImpl("siteop");
			g.getKeyedMap().setObject(GroupManagement.GROUPSLOTS, 0);
			g.getKeyedMap().setObject(GroupManagement.LEECHSLOTS, 0);
			g.commit();
			user.addSecondaryGroup(g);
		} catch (DuplicateElementException ignored) {
		}

		user.commit();
	}

	public User createUser(String username) throws UserFileException, FileExistsException {
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

		User user = createUserImpl(username);
		user.commit();

		return user;
	}

	public Group createGroup(String groupname) throws GroupFileException, FileExistsException {
		try {
			getGroupByName(groupname);
			// bad, .json file already exists.
			throw new FileExistsException("Group " + groupname + " already exists");
		} catch (IOException e) {
			// bad, some I/O error ocurred.
			throw new GroupFileException(e);
		} catch (NoSuchGroupException e) {
			// good, no such group was found. create it!
		}

		Group group = createGroupImpl(groupname);
		group.commit();

		return group;
	}

	protected abstract User createUserImpl(String username);

	protected abstract Group createGroupImpl(String groupname);

	/**
	 * final for now to remove duplicate implementations
	 */
	public synchronized void deleteUser(String username) {
		if (!getUserFile(username).delete())
			throw new RuntimeException(new PermissionDeniedException());
		_users.remove(username);
	}

	public synchronized void deleteGroup(String groupname) {
		if (!getGroupFile(groupname).delete())
			throw new RuntimeException(new PermissionDeniedException());
		_groups.remove(groupname);
	}

	protected abstract File getUserFile(String username);

	protected abstract File getGroupFile(String groupname);

	public abstract Collection<Group> getAllGroups();

	/**
	 * Get all user names in the system.
	 */
	public abstract Collection<User> getAllUsers();

	public Collection<User> getAllUsersByGroup(Group g) {
		Collection<User> c = new ArrayList<>();

		for (User user : getAllUsers()) {

			if (user.isMemberOf(g.getName())) {
				c.add(user);
			}
		}

		return c;
	}

	public boolean isGroupAdminOfUser(User groupadminUser, User requestedUser) {
		List<Group> groups = groupadminUser.getGroups();
		groups.add(groupadminUser.getGroup());

		// Then check secondary groups
		for (Group g : groups) {
			if (g.isAdmin(groupadminUser)) {
				if (requestedUser.isMemberOf(g.getName())) {
					return true;
				}
			}
		}
		return false;
	}

	public boolean isGroupAdmin(User user) {
		List<Group> groups = user.getGroups();
		groups.add(user.getGroup());

		// Then check secondary groups
		for (Group g : groups) {
			if (g.isAdmin(user)) {
				return true;
			}
		}
		return false;
	}

	public Group getGroupByGroupAdminOfUser(User groupadminUser, User requestedUser) {
		List<Group> groups_admin = new ArrayList<>();

		List<Group> groups = groupadminUser.getGroups();
		groups.add(groupadminUser.getGroup());

		for (Group g : groups) {
			if (g.isAdmin(groupadminUser)) {
				groups_admin.add(g);
			}
		}
		// TODO: Think about if we could return more than one group here, but for now we only accept one group to match here
		if (groups_admin.size() == 1) {
			return groups_admin.get(0);
		}
		logger.debug("[getGroupByGroupAdminOfUser] We were unable to find a match between [user:" + requestedUser.getName() + "]'s groups and [user:" +groupadminUser.getName() + "] as group admin. Groups found are: [" + groups_admin.size() + "]");
		return null;
	}

	public User getUserByNameIncludeDeleted(String username) throws NoSuchUserException, UserFileException {
		return getUserByNameUnchecked(username);
	}

	public User getUserByName(String username) throws NoSuchUserException, UserFileException {
		User user = getUserByNameIncludeDeleted(username);

		if (user.isDeleted()) {
			throw new NoSuchUserException(user.getName() + " is deleted");
		}

		return user;
	}

	public Group getGroupByName(String groupname) throws NoSuchGroupException, GroupFileException {
		return getGroupByNameUnchecked(groupname);
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
					if (anIdentArray.matches("^" + botName + "\\|" + ident + "$")) {
						return user;
					}
				}
			} catch (KeyNotFoundException ignored) {
			}
		}
		throw new NoSuchUserException("No user found with ident = " + ident);
	}

	public abstract User getUserByNameUnchecked(String username) throws NoSuchUserException, UserFileException;

	public abstract Group getGroupByNameUnchecked(String groupname) throws NoSuchGroupException, GroupFileException;

	protected synchronized void renameUser(User oldUser, String newUsername) throws UserExistsException, UserFileException {
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

	protected synchronized void renameGroup(Group oldGroup, String newGroupname) throws GroupExistsException, GroupFileException {
		if (!_groups.containsKey(newGroupname)) {
			try {
				getGroupByNameUnchecked(newGroupname);
			} catch (NoSuchGroupException e) {
				_groups.remove(oldGroup.getName());
				_groups.put(newGroupname, new SoftReference<>(oldGroup));
				return;
			}
		}

		throw new GroupExistsException("group " + newGroupname + " exists");
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

	//TODO @k2r Decide what to do with abstract user manager
	private void loadResetHooks() {
		// Load hooks to be run before the reset
	}

	@EventSubscriber
	public synchronized void onUnloadPluginEvent(Object event) {
	}

	@EventSubscriber
	public synchronized void onLoadPluginEvent(Object event) {
	}
}
