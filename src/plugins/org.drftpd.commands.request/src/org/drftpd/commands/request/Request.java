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
package org.drftpd.commands.request;

import java.io.FileNotFoundException;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.dynamicdata.Key;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.exceptions.FileExistsException;
import org.drftpd.master.Session;
import org.drftpd.permissions.Permission;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author mog
 * @version $Id$
 */
public class Request extends CommandInterface {
	public static final Key<Integer> REQUESTSFILLED = new Key<Integer>(Request.class,	"requestsFilled");
	public static final Key<Integer> REQUESTS = new Key<Integer>(Request.class, "requests");
	private static final String FILLEDPREFIX = "FILLED-for.";
	private static final Logger logger = Logger.getLogger(Request.class);
	private static final String REQPREFIX = "REQUEST-by.";

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();
    	_keyPrefix = this.getClass().getName()+".";
    }

	public CommandResponse doSITE_REQFILLED(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		DirectoryHandle currdir = request.getCurrentDirectory();
		Properties props = request.getProperties();
		String requestDirProp = props.getProperty("request.dirpath");
		String reqname = request.getArgument().trim();
		if (requestDirProp != null) {
			currdir = new DirectoryHandle(requestDirProp);
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("ftpuser", request.getUser());
		env.add("fdirname", reqname);
		try {
			for (DirectoryHandle dir : currdir.getDirectoriesUnchecked()) {

				if (!dir.getName().startsWith(REQPREFIX)) {
					continue;
				}

				String username = dir.getName().substring(REQPREFIX.length());
				String myreqname = username.substring(username.indexOf('-') + 1);
				username = username.substring(0, username.indexOf('-'));

				if (myreqname.equals(reqname)) {
					String filledname = FILLEDPREFIX + username + "-" + myreqname;

					try {
						dir.renameToUnchecked(currdir.getNonExistentFileHandle(filledname));
					} catch (FileExistsException e) {
						env.add("fdirname", filledname);
						CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.exists", env, request.getUser()));
						return response;
					} catch (FileNotFoundException e) {
						env.add("fdirname", dir.getName());
						CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.error", env, request.getUser()));
						return response;
					}

					//if (conn.getConfig().checkDirLog(conn.getUserNull(), file)) {
					GlobalContext.getEventService().publishAsync(new DirectoryFtpEvent(
							request.getSession().getUserNull(request.getUser()), "REQFILLED", dir));

					//}
					request.getSession().getUserNull(request.getUser()).getKeyedMap().incrementInt(REQUESTSFILLED);

					CommandResponse response = new CommandResponse(200,
							"OK, renamed " + myreqname + " to " + filledname);
					env.add("fdirname", reqname);
					response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.success", env, request.getUser()));
					return response;
				}
			}
		} catch (FileNotFoundException e) {
			CommandResponse response = new CommandResponse(500, "Request directory does not exist, please CWD /");
			env.add("fdirname", currdir.getName());
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.notfound", env, request.getUser()));
			return response;
		}

		env.add("rdirname", reqname);
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.error", env, request.getUser()));
		return response;
	}

	public CommandResponse doSITE_REQUEST(CommandRequest request) throws ImproperUsageException {
		Session session = request.getSession();
		DirectoryHandle requestDir = request.getCurrentDirectory();
		if (!GlobalContext.getConfig().checkPathPermission("request",
				session.getUserNull(request.getUser()), requestDir)) {
			// if CWD isn't allowed then try falling back to the dir set in command config
			// if such a dir exists
			Properties props = request.getProperties();
			String requestDirProp = props.getProperty("request.dirpath");
			if (requestDirProp == null) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			} else {
				requestDir = new DirectoryHandle(requestDirProp);
			}
			// TODO looks like we haven't implemented a config handler for this permission type
			/*if (!GlobalContext.getConfig().checkPathPermission("request",
					session.getUserNull(request.getUser()), requestDir)) {
				return StandardCommandManager.genericResponse("RESPONSE_530_ACCESS_DENIED");
			}*/
		}

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String createdDirName = REQPREFIX + session.getUserNull(request.getUser()).getName() +
			"-" + request.getArgument().trim();
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("ftpuser", request.getUser());
		env.add("rdirname", request.getArgument().trim());
		try {
			DirectoryHandle createdDir;
			try {
				createdDir = requestDir.createDirectoryUnchecked(createdDirName,
						session.getUserNull(request.getUser()).getName(),
						session.getUserNull(request.getUser()).getGroup());
				User user;
				try {
					user = request.getUserObject();
					user.getKeyedMap().incrementInt(Request.REQUESTS);
				} catch (NoSuchUserException e) {
					// Shouldn't happen as the user must exist to have got this far, log the exception to be safe
					logger.warn("",e);
				} catch (UserFileException e) {
					logger.warn("Error loading userfile for "+request.getUser(),e);
				}
				
			} catch (FileNotFoundException e) {
				CommandResponse response = new CommandResponse(500, "Current directory does not exist, please CWD /");
				env.add("rdirname", requestDir.getPath());
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"request.error", env, request.getUser()));
				return response;
			}

			//if (conn.getConfig().checkDirLog(conn.getUserNull(), createdDir)) {
			GlobalContext.getEventService().publishAsync(new DirectoryFtpEvent(
					session.getUserNull(request.getUser()), "REQUEST", createdDir));

			session.getUserNull(request.getUser()).getKeyedMap().incrementInt(REQUESTS);

			//conn.getUser().addRequests();
			CommandResponse response = new CommandResponse(257, "\"" + createdDir.getPath() +
				"\" created.");
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"request.success", env, request.getUser()));
			return response;
		} catch (FileExistsException ex) {
			CommandResponse response = new CommandResponse(550,
					"directory " + createdDirName + " already exists");
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"request.exists", env, request.getUser()));
			return response;
		}
	}

	public CommandResponse doSITE_REQUESTS(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}
		Properties props = request.getProperties();
		String requestDirProp = props.getProperty("request.dirpath");
		if (requestDirProp == null) {
			return new CommandResponse(500, "Requests path not set in command conf");
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"requests.header", env, request.getUser()));
		int i = 1;
		
		User user = request.getSession().getUserNull(request.getUser());
		
		try {
			for (DirectoryHandle dir : new DirectoryHandle(requestDirProp).getDirectories(user)) {
				if (!dir.getName().startsWith(REQPREFIX)) {
					continue;
				}
				String username = dir.getName().substring(REQPREFIX.length());
				String reqname = username.substring(username.indexOf('-') + 1);
				username = username.substring(0, username.indexOf('-'));
				env.add("num",Integer.toString(i));
                env.add("requser",username);
                env.add("reqrequest",reqname);
                i = i+1;
                response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"requests.list", env, request.getUser()));
			}
		} catch (FileNotFoundException e) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"request.error", env, request.getUser()));
		}
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"requests.footer", env, request.getUser()));
		return response;
	}

	public CommandResponse doSITE_REQDELETE(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		DirectoryHandle currdir = request.getCurrentDirectory();
		Properties props = request.getProperties();
		String requestDirProp = props.getProperty("request.dirpath");
		String deleteOthers = props.getProperty("deleteOthers","=siteop");
		String reqname = request.getArgument().trim();
		if (requestDirProp != null) {
			currdir = new DirectoryHandle(requestDirProp);
		}
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("ftpuser", request.getUser());
		env.add("ddirname", reqname);
		boolean nodir = false;
		boolean deldir = false;
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		User user;
		try {
			user = request.getUserObject();
		} catch (NoSuchUserException e) {
			// Shouldn't happen as user must exist to get here, log exception
			logger.warn("",e);
			return response;
		} catch (UserFileException e) {
			logger.warn("Error loading userfile for "+request.getUser(),e);
			return response;
		}
		
		try {
			for (DirectoryHandle dir : currdir.getDirectories(user)) {
				if (dir.getName().endsWith(reqname)) {
					nodir = false;
					if (dir.getUsername().equals(request.getUser())
							|| new Permission(deleteOthers).check(user)) {
						dir.deleteUnchecked();
						deldir = true;
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.success", env, request.getUser()));
						break;
					} else {
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.notowner", env, request.getUser()));
						break;
					}
				} else {
					nodir = true;
				}
			}
			if (nodir && !deldir) {
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.notfound", env, request.getUser()));
			}
		} catch (FileNotFoundException e) {
			env.add("ddirname", currdir.getName());
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.error", env, request.getUser()));
		}
		return response;
	}

}
