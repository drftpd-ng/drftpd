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
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author mog
 * @version $Id$
 */
public class Request extends CommandInterface {
	public static final Key<Integer> REQUESTSFILLED = new Key<Integer>(Request.class,	"requestsFilled");
	public static final Key<Integer> REQUESTS = new Key<Integer>(Request.class, "requests");
	
	private static final Logger logger = Logger.getLogger(Request.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;
	
	private String _requestPath;
	private boolean _createRequestPath;
	
	private String _reqFilledPrefix;
	private String _requestPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
    	_bundle = cManager.getResourceBundle();
    	_keyPrefix = this.getClass().getName()+".";
    	
    	readConfig();
    	
    	createDirectory();
    }

	/**
	 * Reads 'conf/plugins/requests.conf'
	 */
	private void readConfig() {
		Properties props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("requests");
		
		_requestPath = props.getProperty("request.dirpath", "/requests/");
		_createRequestPath = Boolean.parseBoolean(props.getProperty("request.createpath", "false"));
		
		_reqFilledPrefix = props.getProperty("reqfilled.prefix", "FILLED-for.");
		_requestPrefix = props.getProperty("request.prefix", "REQUEST-by.");
	}
	
	/**
	 * Create the request directory if it does not exist and 'request.createpath' is <code>true</code>
	 */
	private void createDirectory() {
		DirectoryHandle requestDir = new DirectoryHandle(_requestPath);

    	if (_createRequestPath && !requestDir.exists()) {
    		try {
				requestDir.getParent().createDirectoryRecursive(requestDir.getName());
			} catch (FileExistsException e) {
				logger.error("Tried to create a directory that already exists during request plugin initialization.", e);
			} catch (FileNotFoundException e) {
				logger.error("How did this happened? It was there couple lines above", e);
			}
    	}		
	}
	
	/**
	 * If the commands has a 'request.dirpath' set we will use this one
	 * otherwise we will use the fallback/default path set in 'conf/plugins/requests.conf'
	 * 
	 * This allows multiple request dirs.
	 * @param request
	 * @return a {@link DirectoryHandle} representing the correct request dir.
	 */
	private DirectoryHandle getRequestDirectory(CommandRequest request) {
		String requestDirProp = request.getProperties().getProperty("request.dirpath");
		if (requestDirProp == null) {
			return new DirectoryHandle(_requestPath);
		} else {
			return new DirectoryHandle(requestDirProp);
		}
	}

	public CommandResponse doSITE_REQFILLED(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
		
		User user = request.getSession().getUserNull(request.getUser());
		DirectoryHandle requestDir = getRequestDirectory(request);
		String requestName = request.getArgument().trim();
		
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("user", user);
		env.add("request.name", requestName);
		
		try {
			for (DirectoryHandle dir : requestDir.getDirectoriesUnchecked()) {

				if (!dir.getName().startsWith(_requestPrefix)) {
					continue;
				}

				RequestParser parser = new RequestParser(dir.getName());

				if (parser.getRequestName().equals(requestName)) {
					String filledname = _reqFilledPrefix + parser.getUser() + "-" + parser.getRequestName();

					try {
						dir.renameToUnchecked(requestDir.getNonExistentDirectoryHandle(filledname));
					} catch (FileExistsException e) {
						return new CommandResponse(500, request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.exists", env, request.getUser()));
					} catch (FileNotFoundException e) {
						logger.error("File was just here but it vanished", e);
						return new CommandResponse(500, request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.error", env, request.getUser()));
					}

					// TODO revisit his (replace with an IrcAnnouncer?)
					GlobalContext.getEventService().publishAsync(new DirectoryFtpEvent(request.getSession().getUserNull(request.getUser()), "REQFILLED", dir));

					// TODO PostHook to increment REQFILLED

					return new CommandResponse(200, request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.success", env, request.getUser()));
				}
			}
		} catch (FileNotFoundException e) {
			return new CommandResponse(500, request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.root.notfound", env, request.getUser()));
		}

		return new CommandResponse(500, request.getSession().jprintf(_bundle, _keyPrefix+"reqfilled.notfound", env, request.getUser()));
	}

	public CommandResponse doSITE_REQUEST(CommandRequest request) throws ImproperUsageException {
		Session session = request.getSession();
		
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		User user = session.getUserNull(request.getUser());
		String createdDirName = _requestPrefix + user.getName() +	"-" + request.getArgument().trim();
		DirectoryHandle requestDir = getRequestDirectory(request);
		
		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("user", request.getUser());
		env.add("request.name", request.getArgument().trim());
		env.add("request.root", requestDir.getPath());
		
		DirectoryHandle createdDir = null;
		try {
			createdDir = requestDir.createDirectoryUnchecked(createdDirName, user.getName(), user.getGroup());
		} catch (FileExistsException e) {
			return new CommandResponse(550, session.jprintf(_bundle, _keyPrefix+"request.exists", env, user.getName()));
		} catch (FileNotFoundException e) {
			logger.error("File was just here but it vanished", e);
			return new CommandResponse(550, session.jprintf(_bundle, _keyPrefix+"request.error", env, user.getName()));
		}
		
		// TODO Post Hook to increment request number
		
		// TODO revisit his (replace with an IrcAnnouncer?)
		GlobalContext.getEventService().publishAsync(new DirectoryFtpEvent(session.getUserNull(request.getUser()), "REQUEST", createdDir));
		
		return new CommandResponse(257, session.jprintf(_bundle, _keyPrefix+"request.success", env, user.getName()));
	}

	public CommandResponse doSITE_REQUESTS(CommandRequest request) throws ImproperUsageException {
		if (request.hasArgument()) {
			throw new ImproperUsageException();
		}
		
		ReplacerEnvironment env = new ReplacerEnvironment();
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"requests.header", env, request.getUser()));
		int i = 1;
		
		User user = request.getSession().getUserNull(request.getUser());
		
		try {
			for (DirectoryHandle dir : getRequestDirectory(request).getDirectories(user)) {
				if (!dir.getName().startsWith(_requestPrefix)) {
					continue;
				}
				
				RequestParser parser = new RequestParser(dir.getName());
				
				env.add("num",Integer.toString(i));
                env.add("request.user",parser.getUser());
                env.add("request.name",parser.getRequestName());
                
                i++;
                
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

		User user = request.getSession().getUserNull(request.getUser());
		String requestName = request.getArgument().trim();
		String deleteOthers = request.getProperties().getProperty("request.deleteOthers","=siteop");
		DirectoryHandle requestDir = getRequestDirectory(request);

		ReplacerEnvironment env = new ReplacerEnvironment();
		env.add("user", user.getName());
		env.add("request.name", requestName);
		env.add("request.root", requestDir.getPath());
		
		boolean requestNotFound = true;
		
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		
		try {
			for (DirectoryHandle dir : requestDir.getDirectories(user)) {
				
				if (!dir.getName().startsWith(_requestPrefix)) {
					continue;
				}
				
				RequestParser parser = new RequestParser(dir.getName());
				
				if (parser.getRequestName().equals(requestName)) {
					requestNotFound = false;
					
					// checking if the user trying to delete this request
					// is either the owner or has "super-powers"
					if (parser.getUser().equals(user.getName()) ||
							new Permission(deleteOthers).check(user)) {
						
						dir.deleteUnchecked();
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.success", env, request.getUser()));
						
						// TODO fire event?!
						// TODO decrement the weekly request amount? (not sure if wanted, make configurable?)
						
						break;
					} else {
						response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.notowner", env, request.getUser()));
						break;
					}
				}
			}
			
			if (requestNotFound) {
				response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.notfound", env, request.getUser()));
			}
			
		} catch (FileNotFoundException e) {
			response.addComment(request.getSession().jprintf(_bundle, _keyPrefix+"reqdel.root.notfound", env, request.getUser()));
		}
		
		return response;
	}
	
	/**
	 * Class to centralize how requests are parsed.
	 * 
	 * Transforms a 'request.prefix'-'user'-'requestname' in
	 * a nice looking data structure instead of simple strings. 
	 */
	private class RequestParser {
		private String _user;
		private String _requestName;
		
		public RequestParser(String dirname) {
			_user= dirname.substring(_requestPrefix.length());
			_requestName = _user.substring(_user.indexOf('-') + 1);
			_user = _user.substring(0, _user.indexOf('-'));
		}
		
		public String getUser() {
			return _user;
		}
		
		public String getRequestName() {
			return _requestName;
		}
	}

}
