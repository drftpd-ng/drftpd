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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
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
import java.util.LinkedList;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 * @version $Id$
 */
public class Search extends CommandInterface {
	public static final Logger logger = LogManager.getLogger(Find.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public CommandResponse doSEARCH(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		AdvancedSearchParams params = new AdvancedSearchParams();

		if (request.getProperties().getProperty("exact","false").equals("true")) {
			params.setExact(request.getArgument());
		} else {
			params.setName(request.getArgument());
		}

		String type = request.getProperties().getProperty("type");
		if (type != null && type.equals("d"))
			params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
		else if (type != null && type.equals("f"))
			params.setInodeType(AdvancedSearchParams.InodeType.FILE);

		int limit = Integer.parseInt(request.getProperties().getProperty("limit","5"));
		params.setLimit(0); // Get all results, we filter out hidden inodes later

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

		LinkedList<String> responses = new LinkedList<>();

		boolean observePrivPath = request.getProperties().
				getProperty("observe.privpath","true").equalsIgnoreCase("true");
		
		InodeHandle inode;
		for (Map.Entry<String,String> item : inodes.entrySet()) {
			if (responses.size() == limit)
				break;
			try {
				inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey().
						substring(0, item.getKey().length()-1)) : new FileHandle(item.getKey());
				if ((observePrivPath && inode.isHidden(user)) || (!observePrivPath && inode.isHidden(null))) {
					continue;
				}
				env.add("name", inode.getName());
				env.add("path", inode.getPath());
				env.add("owner", inode.getUsername());
				env.add("group", inode.getGroup());
				env.add("size", Bytes.formatBytes(inode.getSize()));
				responses.add(session.jprintf(_bundle,_keyPrefix+"search.item", env, user.getName()));
			} catch (FileNotFoundException e) {
                logger.warn("Index contained an unexistent inode: {}", item.getKey());
			}
		}

		if (responses.isEmpty()) {
			response.addComment(session.jprintf(_bundle,_keyPrefix+"search.empty", env, user.getName()));
			return response;
		}

		env.add("limit", limit);
		env.add("results", responses.size());
		response.addComment(session.jprintf(_bundle,_keyPrefix+"search.header", env, user.getName()));

		for (String line : responses) {
			response.addComment(line);
		}

		return response;
	}
}
