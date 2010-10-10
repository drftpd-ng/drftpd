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
package org.drftpd.commands.find;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.master.Session;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @author scitz0
 * @version $Id$
 */
public class Search extends CommandInterface {
	public static final Logger logger = Logger.getLogger(Find.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSearch(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		params.setName(request.getArgument());
		params.setLimit(Integer.parseInt(request.getProperties().getProperty("limit","5")));

		return advSearch(request, params);
	}

	public CommandResponse doDupe(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		params.setName(request.getArgument());
		params.setExact(true);
		params.setLimit(Integer.parseInt(request.getProperties().getProperty("limit","5")));

		return advSearch(request, params);
	}

	public CommandResponse doSuperSearch(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		int limit = Integer.parseInt(request.getProperties().getProperty("limit.default","5"));
		int maxLimit = Integer.parseInt(request.getProperties().getProperty("limit.max","20"));

		StringTokenizer st = new StringTokenizer(request.getArgument());

		while(st.hasMoreTokens()) {
			String option = st.nextToken();

			if (option.equalsIgnoreCase("-f") || option.equalsIgnoreCase("-file")) {
				params.setInodeType(AdvancedSearchParams.InodeType.FILE);
			} else if (option.equalsIgnoreCase("-d") || option.equalsIgnoreCase("-dir")) {
				params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
			} else if (!st.hasMoreTokens()) {
				throw new ImproperUsageException();
			} else if (option.equalsIgnoreCase("-user")) {
				params.setOwner(st.nextToken());
			} else if (option.equalsIgnoreCase("-group")) {
				params.setGroup(st.nextToken());
			} else if (option.equalsIgnoreCase("-slaves")) {
				HashSet<String> slaves = new HashSet<String>(Arrays.asList(st.nextToken().split(",")));
				params.setSlaves(slaves);
			} else if (option.equalsIgnoreCase("-age")) {
				SimpleDateFormat fullDate = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss");
				SimpleDateFormat shortDate = new SimpleDateFormat("yyyy.MM.dd");
				try {
					String from = st.nextToken();
					String to = st.nextToken();

					long minAge;
					long maxAge;

					if (from.length() == 10)
						minAge = shortDate.parse(from).getTime();
					else if (from.length() == 19)
						minAge = fullDate.parse(from).getTime();
					else
						throw new ImproperUsageException("Invalid dateformat for min age in index search.");

					if (to.length() == 10)
						maxAge = shortDate.parse(to).getTime();
					else if (to.length() == 19)
						maxAge = fullDate.parse(to).getTime();
					else
						throw new ImproperUsageException("Invalid dateformat for max age in index search.");

					if (minAge >= maxAge)
						throw new ImproperUsageException("Age range invalid, min value higher or same as max");

					params.setMinAge(minAge);
					params.setMaxAge(maxAge);
				} catch (NumberFormatException e) {
					throw new ImproperUsageException(e);
				} catch (NoSuchElementException e) {
					throw new ImproperUsageException("You must specify a range for the age, both min and max", e);
				}  catch (ParseException e) {
					throw new ImproperUsageException("Invalid dateformat", e);
				}
			} else if (option.equalsIgnoreCase("-size")) {
				try {
					long minSize = Bytes.parseBytes(st.nextToken());
					long maxSize = Bytes.parseBytes(st.nextToken());
					if (minSize >= maxSize) {
						throw new ImproperUsageException("Size range invalid, min value higher or same as max");
					}
					params.setMinSize(minSize);
					params.setMaxSize(maxSize);
				} catch (NumberFormatException e) {
					throw new ImproperUsageException(e);
				} catch (NoSuchElementException e) {
					throw new ImproperUsageException("You must specify a range for the size, both min and max", e);
				}
			} else if (option.equalsIgnoreCase("-sort")) {
				String field = st.nextToken();
				params.setSortField(field);
				if (!st.hasMoreTokens()) {
					throw new ImproperUsageException("You must specify both field and sort order");
				}
				String order = st.nextToken();
				if (order.equalsIgnoreCase("asc")) {
					params.setSortOrder(false);
				} else {
					params.setSortOrder(true);
				}
			} else if (option.equalsIgnoreCase("-name")) {
				params.setName(st.nextToken("").trim());
			} else if (option.equalsIgnoreCase("-exact")) {
				params.setExact(true);
			} else if (option.equalsIgnoreCase("-endswith")) {
				params.setEndsWith(st.nextToken());
			} else if (option.equalsIgnoreCase("-limit")) {
				try {
					int newLimit = Integer.parseInt(st.nextToken());
					if (newLimit < maxLimit) {
						limit = newLimit;
					} else {
						limit = maxLimit;
					}
				} catch (NumberFormatException e) {
					throw new ImproperUsageException("Limit must be valid number.");
				}
			}
		}

		params.setLimit(limit);

		return advSearch(request, params);
	}

	private CommandResponse advSearch(CommandRequest request, AdvancedSearchParams params) {
		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		try {
			inodes = ie.advancedFind(request.getCurrentDirectory(), params);
		} catch (IndexException e) {
			logger.error(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		} catch (IllegalArgumentException e) {
			logger.info(e.getMessage());
			return new CommandResponse(550, e.getMessage());
		}

		ReplacerEnvironment env = new ReplacerEnvironment();

		User user = request.getSession().getUserNull(request.getUser());

		Session session = request.getSession();

		CommandResponse response = new CommandResponse(200, "Search complete");

		response.addComment(session.jprintf(_bundle,_keyPrefix+"search.header", env, user.getName()));

		if (inodes.isEmpty()) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"search.empty", env, user.getName()));
			return response;
		}

		InodeHandle inode;
		for (Map.Entry<String,String> item : inodes.entrySet()) {
			try {
				inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey()) :
						new FileHandle(item.getKey());
				if (!inode.isHidden(user)) {
					env.add("name", inode.getName());
					env.add("path", inode.getPath());
					env.add("owner", inode.getUsername());
					env.add("group", inode.getGroup());
					env.add("size", Bytes.formatBytes(inode.getSize()));
					response.addComment(session.jprintf(_bundle,_keyPrefix+"search.item", env, user.getName()));
				}
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + item.getKey());
			}
		}

		return response;
	}
}
