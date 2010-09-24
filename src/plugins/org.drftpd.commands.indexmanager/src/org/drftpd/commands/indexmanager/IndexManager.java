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
package org.drftpd.commands.indexmanager;

import java.io.FileNotFoundException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Map;
import java.util.Map.Entry;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.StringTokenizer;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.drftpd.vfs.index.AdvancedSearchParams.InodeType;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author fr0w
 * @version $Id$
 */
public class IndexManager extends CommandInterface {
	private static final Logger logger = Logger.getLogger(IndexManager.class);
	

	private ResourceBundle _bundle;
	private String _keyPrefix;
	
	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doRebuildIndex(CommandRequest request) {
		CommandResponse response = new CommandResponse(200, "Index rebuilt");

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		try {
			ie.rebuildIndex();
		} catch (IndexException e) {
			return new CommandResponse(550, e.getMessage());
		}

		response.addComment("Entries in the index: " + ie.getStatus().get("inodes"));

		return response;
	}
	
	public CommandResponse doIndexStatus(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		
		if (request.getArgument().equalsIgnoreCase("full")) {
			for (Entry<String,String> entry : ie.getStatus().entrySet()) {
				env.add("key", entry.getKey());
				env.add("value", entry.getValue());
				response.addComment(request.getSession().jprintf(_bundle,_keyPrefix+"indexstatus", env, request.getUser()));
			}
		} else {
			response.addComment("Entries in the index: " + ie.getStatus().get("inodes"));
		}

		return response;
	}

	public CommandResponse doSearchIndex(CommandRequest request, boolean isDir) {
		CommandResponse response = new CommandResponse(200, "Search complete");
		User user = request.getSession().getUserNull(request.getUser());

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Set<String> inodes;

		try {
			InodeType inodeType = isDir ? InodeType.DIRECTORY : InodeType.FILE;
			inodes = ie.findInode(request.getCurrentDirectory(), request.getArgument(), inodeType);			
		} catch (IndexException e) {
			return new CommandResponse(550, e.getMessage());
		}
		
		InodeHandle inode;
		for (String path : inodes) {
			try {
				inode = isDir ? new DirectoryHandle(path) : new FileHandle(path);
				if (!inode.isHidden(user)) {
					response.addComment(inode.getPath());
				}
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + path);
			}
		}

		return response;
	}

	public CommandResponse doSearchDir(CommandRequest request) {
		return doSearchIndex(request, true);
	}

	public CommandResponse doSearchFile(CommandRequest request) {
		return doSearchIndex(request, false);
	}

	public CommandResponse doAdvSearch(CommandRequest request) throws ImproperUsageException {

		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		CommandResponse response = new CommandResponse(200, "Search complete");

		AdvancedSearchParams params = new AdvancedSearchParams();

		StringTokenizer st = new StringTokenizer(request.getArgument());

		while(st.hasMoreTokens()) {
			String option = st.nextToken();

			if (!st.hasMoreTokens()) {
				throw new ImproperUsageException();
			} else if (option.equalsIgnoreCase("-type")) {
				String type = st.nextToken();
				if (type.equalsIgnoreCase("f") || type.equalsIgnoreCase("file")) {
					params.setInodeType(InodeType.FILE);
				} else if (type.equalsIgnoreCase("d") || type.equalsIgnoreCase("dir")) {
					params.setInodeType(InodeType.DIRECTORY);
				} else {
					throw new ImproperUsageException();
				}
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
			} else if (option.equalsIgnoreCase("-name")) {
				StringBuilder nameQuery = new StringBuilder();
				while(st.hasMoreTokens()) {
					nameQuery.append(st.nextToken()).append(" ");
				}
				params.setName(nameQuery.toString().trim());
			}
		}

		IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
		Map<String,String> inodes;

		try {
			inodes = ie.advancedFind(request.getCurrentDirectory(), params);
		} catch (IndexException e) {
			return new CommandResponse(550, e.getMessage());
		}

		User user = request.getSession().getUserNull(request.getUser());

		InodeHandle inode;
		for (Entry<String,String> item : inodes.entrySet()) {
			try {
				inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey()) :
						new FileHandle(item.getKey());
				if (!inode.isHidden(user)) {
					response.addComment(inode.getPath());
				}
			} catch (FileNotFoundException e) {
				logger.warn("Index contained an unexistent inode: " + item.getKey());
			}
		}

		return response;
	}
}
