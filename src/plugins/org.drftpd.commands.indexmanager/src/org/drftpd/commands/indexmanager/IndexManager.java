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

import java.util.Map.Entry;
import java.util.ResourceBundle;
import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
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
}
