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

import java.io.File;
import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commands.UserManagement;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.LoadPluginEvent;
import org.drftpd.event.UnloadPluginEvent;
import org.drftpd.exceptions.DuplicateElementException;
import org.drftpd.exceptions.FileExistsException;
import org.java.plugin.PluginManager;
import org.java.plugin.registry.Extension;
import org.java.plugin.registry.ExtensionPoint;

import se.mog.io.PermissionDeniedException;

/**
 * This is the base class of all the user manager classes. If we want to add a
 * new user manager, we have to override this class.
 * 
 * @author <a href="mailto:rana_b@yahoo.com">Rana Bhattacharyya </a>
 * @version $Id$
 */
public abstract class AbstractUserManager implements UserManager {
	private static final Logger logger = Logger.getLogger(AbstractUserManager.class);

	protected HashMap<String, SoftReference<User>> _users;

	private ArrayList<UserResetHookInterface> _preResetHooks = new ArrayList<UserResetHookInterface>();

	private ArrayList<UserResetHookInterface> _postResetHooks = new ArrayList<UserResetHookInterface>();

	public void init() throws UserFileException {
		// Subscribe to events
		AnnotationProcessor.process(this);
		loadResetHooks();
	}

	protected abstract File getUserpathFile();

	protected void createSiteopUser() throws UserFileException {
		User user = createUser("drftpd");
		user.setGroup("drftpd");
		user.setPassword("drftpd");
		user.getKeyedMap().setObject(UserManagement.RATIO, new Float(0));
		user.getKeyedMap().setObject(UserManagement.GROUPSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.LEECHSLOTS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINS, 0);
		user.getKeyedMap().setObject(UserManagement.MAXLOGINSIP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMUP, 0);
		user.getKeyedMap().setObject(UserManagement.MAXSIMDN, 0);
		// user.getKeyedMap().setObject(Statistics.LOGINS,0);
		user.getKeyedMap().setObject(UserManagement.CREATED, new Date());
		user.getKeyedMap().setObject(UserManagement.LASTSEEN, new Date());
		user.getKeyedMap()
		.setObject(UserManagement.WKLY_ALLOTMENT, new Long(0));
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
			// bad, .xml file already exists.
			throw new FileExistsException("User " +username+ " already exists");
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
		ArrayList<String> ret = new ArrayList<String>();

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
		Collection<User> c = new ArrayList<User>();

		for (User user : getAllUsers()) {

			if (user.isMemberOf(group)) {
				c.add(user);
			}
		}

		return c;
	}

	public User getUserByNameIncludeDeleted(String username)
	throws NoSuchUserException, UserFileException {
		User user = getUserByNameUnchecked(username);
		return user;
	}

	public User getUserByName(String username) throws NoSuchUserException,
	UserFileException {
		User user = getUserByNameIncludeDeleted(username);

		if (user.isDeleted()) {
			throw new NoSuchUserException(user.getName() + " is deleted");
		}

		return user;
	}

	public static GlobalContext getGlobalContext() {
		return GlobalContext.getGlobalContext();
	}

	public User getUserByIdent(String ident, String botName) throws NoSuchUserException {
		for (User user : getAllUsers()) {
			try {
				String uidentList = (String) user.getKeyedMap().getObject(
						UserManagement.IRCIDENT);
				String[] identArray = uidentList.split(",");
				for (int i = 0; i < identArray.length;i++) {
					if (identArray[i].startsWith(botName)) {
						String[] botIdent = identArray[i].split("\\|");
						if (botIdent.length == 2) {
							if (botIdent[1].equals(ident)) {
								return user;
							}
						}
					}
				}
			} catch (KeyNotFoundException e1) {
			}
		}
		throw new NoSuchUserException("No user found with ident = " + ident);
	}

	public abstract User getUserByNameUnchecked(String username)
	throws NoSuchUserException, UserFileException;

	protected synchronized void rename(User oldUser, String newUsername)
	throws UserExistsException, UserFileException {
		if (!_users.containsKey(newUsername)) {
			try {
				getUserByNameUnchecked(newUsername);
			} catch (NoSuchUserException e) {
				_users.remove(oldUser.getName());
				_users.put(newUsername, new SoftReference<User>(oldUser));
				return;
			}
		}

		throw new UserExistsException("user " + newUsername + " exists");
	}

	/* (non-Javadoc)
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

	/* (non-Javadoc)
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


	/* (non-Javadoc)
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

	/* (non-Javadoc)
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

	/* (non-Javadoc)
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
		PluginManager manager = PluginManager.lookup(this);
		ExtensionPoint prExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"master", "PreUserResetHook");

		// Iterate over all extensions that have been connected to the
		// PreUserResetHook extension point, init them and add to list

		for (Extension pr : prExtPoint.getConnectedExtensions()) {
			try {
				manager.activatePlugin(pr.getDeclaringPluginDescriptor().getId());
				ClassLoader prLoader = manager.getPluginClassLoader( 
						pr.getDeclaringPluginDescriptor());
				Class<?> prCls = prLoader.loadClass( 
						pr.getParameter("Class").valueAsString());
				UserResetHookInterface preResetHook = (UserResetHookInterface) prCls.newInstance();
				preResetHook.init();
				_preResetHooks.add(preResetHook);
				logger.debug("Loading PreUserResetHook into UserManager "+manager.getPluginFor(preResetHook).getDescriptor().getId());
			}
			catch (Exception e) {
				logger.warn("Failed to load PreUserResetHook extension to usermanager",e);
			}
		}

		ExtensionPoint poExtPoint = 
			manager.getRegistry().getExtensionPoint( 
					"master", "PostUserResetHook");

		// Iterate over all extensions that have been connected to the
		// PostUserResetHook extension point, init them and add to list

		for (Extension po : poExtPoint.getConnectedExtensions()) {
			try {
				manager.activatePlugin(po.getDeclaringPluginDescriptor().getId());
				ClassLoader poLoader = manager.getPluginClassLoader( 
						po.getDeclaringPluginDescriptor());
				Class<?> poCls = poLoader.loadClass( 
						po.getParameter("Class").valueAsString());
				UserResetHookInterface postResetHook = (UserResetHookInterface) poCls.newInstance();
				postResetHook.init();
				_postResetHooks.add(postResetHook);
				logger.debug("Loading PostUserResetHook into UserManager "+manager.getPluginFor(postResetHook).getDescriptor().getId());
			}
			catch (Exception e) {
				logger.warn("Failed to load PostUserResetHook extension to usermanager",e);
			}
		}
	}

	@EventSubscriber
	public void onUnloadPluginEvent(UnloadPluginEvent event) {
		PluginManager manager = PluginManager.lookup(this);
		String currentPlugin = manager.getPluginFor(this.getClass().getSuperclass()).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin)) {
				if (extension.equals("PreUserResetHook")) {
					for (Iterator<UserResetHookInterface> iter = _preResetHooks.iterator(); iter.hasNext();) {
						UserResetHookInterface preResetHook = iter.next();
						if (manager.getPluginFor(preResetHook).getDescriptor().getId().equals(event.getPlugin())) {
							logger.debug("Unloading PreUserResetHook from UserManager "+manager.getPluginFor(preResetHook).getDescriptor().getId());
							iter.remove();
						}
					}
				}
				if (extension.equals("PostUserResetHook")) {
					for (Iterator<UserResetHookInterface> iter = _postResetHooks.iterator(); iter.hasNext();) {
						UserResetHookInterface postResetHook = iter.next();
						if (manager.getPluginFor(postResetHook).getDescriptor().getId().equals(event.getPlugin())) {
							logger.debug("Unloading PostUserResetHook from UserManager "+manager.getPluginFor(postResetHook).getDescriptor().getId());
							iter.remove();
						}
					}
				}
			}
		}
	}

	@EventSubscriber
	public void onLoadPluginEvent(LoadPluginEvent event) {
		PluginManager manager = PluginManager.lookup(this);
		String currentPlugin = manager.getPluginFor(this.getClass().getSuperclass()).getDescriptor().getId();
		for (String pluginExtension : event.getParentPlugins()) {
			int pointIndex = pluginExtension.lastIndexOf("@");
			String pluginName = pluginExtension.substring(0, pointIndex);
			String extension = pluginExtension.substring(pointIndex+1);
			if (pluginName.equals(currentPlugin)) {
				if (extension.equals("PreUserResetHook")) {
					ExtensionPoint prExtPoint = 
						manager.getRegistry().getExtensionPoint( 
								"master", "PreUserResetHook");
					for (Extension pr : prExtPoint.getConnectedExtensions()) {
						if (pr.getDeclaringPluginDescriptor().getId().equals(event.getPlugin())) {
							try {
								manager.activatePlugin(pr.getDeclaringPluginDescriptor().getId());
								ClassLoader prLoader = manager.getPluginClassLoader( 
										pr.getDeclaringPluginDescriptor());
								Class<?> prCls = prLoader.loadClass( 
										pr.getParameter("Class").valueAsString());
								UserResetHookInterface preResetHook = (UserResetHookInterface) prCls.newInstance();
								preResetHook.init();
								_preResetHooks.add(preResetHook);
								logger.debug("Loading PreUserResetHook into UserManager "+manager.getPluginFor(preResetHook).getDescriptor().getId());
							}
							catch (Exception e) {
								logger.warn("Error loading PreUserResetHook extension to UserManager " + 
										pr.getDeclaringPluginDescriptor().getId(),e);
							}
						}
					}
				}
				if (extension.equals("PostUserResetHook")) {
					ExtensionPoint poExtPoint = 
						manager.getRegistry().getExtensionPoint( 
								"master", "PostUserResetHook");
					for (Extension po : poExtPoint.getConnectedExtensions()) {
						if (po.getDeclaringPluginDescriptor().getId().equals(event.getPlugin())) {
							try {
								manager.activatePlugin(po.getDeclaringPluginDescriptor().getId());
								ClassLoader poLoader = manager.getPluginClassLoader( 
										po.getDeclaringPluginDescriptor());
								Class<?> poCls = poLoader.loadClass( 
										po.getParameter("Class").valueAsString());
								UserResetHookInterface postResetHook = (UserResetHookInterface) poCls.newInstance();
								postResetHook.init();
								_postResetHooks.add(postResetHook);
								logger.debug("Loading PostUserResetHook into UserManager "+manager.getPluginFor(postResetHook).getDescriptor().getId());
							}
							catch (Exception e) {
								logger.warn("Error loading PostUserResetHook extension to UserManager " + 
										po.getDeclaringPluginDescriptor().getId(),e);
							}
						}
					}
				}
			}
		}
	}
}
