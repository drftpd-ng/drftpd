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
package org.drftpd.commands.approve;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.approve.metadata.Approve;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.master.Session;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Map;
import java.util.ResourceBundle;

public class ApproveCommands extends CommandInterface {
	private static final Logger logger = LogManager.getLogger(ApproveCommands.class);

	private ResourceBundle _bundle;
	private String _keyPrefix;

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
    	super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
    }

	public CommandResponse doSITE_REMAPPROVE(CommandRequest request) {
		return doApprove(request,true);
	}
	
	public CommandResponse doSITE_APPROVE(CommandRequest request) {
		return doApprove(request,false);
	}
	
	public CommandResponse doApprove(CommandRequest request,boolean doremove) {
		Session session = request.getSession();
		User user = session.getUserNull(request.getUser());
		DirectoryHandle dir = request.getCurrentDirectory();
		boolean remove = doremove;
		if (request.hasArgument() && (user != null)) {
			String path = request.getArgument();
			if (path.startsWith("-r ")) {
				// remove option specified
				remove = true;
				path = path.substring(3);
			}
			if (path.charAt(0) != '/') {
				// Full path not given, try to get it with index system
				IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();

				Map<String,String> inodes;

				AdvancedSearchParams params = new AdvancedSearchParams();
				params.setExact(path);
				params.setInodeType(AdvancedSearchParams.InodeType.DIRECTORY);
				params.setSortField("lastmodified");
				params.setSortOrder(true);

				try {
					inodes = ie.advancedFind(dir, params);
				} catch (IndexException e) {
                    logger.error("Index Exception: {}", e.getMessage());
					return new CommandResponse(500, "Index Exception: " + e.getMessage());
				}

				ArrayList<DirectoryHandle> dirsToApprove = new ArrayList<>();

				for (Map.Entry<String,String> item : inodes.entrySet()) {
					try {
						DirectoryHandle inode = new DirectoryHandle(VirtualFileSystem.fixPath(item.getKey()));
						if (!inode.isHidden(user)) {
							dirsToApprove.add(inode);
						}
					} catch (FileNotFoundException e) {
                        logger.warn("Index contained an unexistent inode: {}", item.getKey());
					}
				}

				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("user", user.getName());
				env.add("searchstr", path);
				if (dirsToApprove.isEmpty()) {
					return new CommandResponse(550, session.jprintf(_bundle,_keyPrefix+"approve.search.empty",env, user));
				} else if (dirsToApprove.size() == 1) {
					path = dirsToApprove.get(0).getPath();
				} else {
					CommandResponse response = new CommandResponse(200);
					response.addComment(session.jprintf(_bundle,_keyPrefix+"approve.search.start", env, user));
					int count = 1;
					for (DirectoryHandle foundDir : dirsToApprove) {
						try {
							env.add("name", foundDir.getName());
							env.add("path", foundDir.getPath());
							env.add("owner", foundDir.getUsername());
							env.add("group", foundDir.getGroup());
							env.add("num", count++);
							env.add("size", Bytes.formatBytes(foundDir.getSize()));
							response.addComment(session.jprintf(_bundle,_keyPrefix+"approve.search.item", env, user));
						} catch (FileNotFoundException e) {
                            logger.warn("Dir deleted after index search?, skip and continue: {}", foundDir.getPath());
						}
					}

					response.addComment(session.jprintf(_bundle,_keyPrefix+"approve.search.end", env, user));

					// Return matching dirs and let user decide what to approve
					return response;
				}
			}
			dir = new DirectoryHandle(path);
			if (!dir.exists()) {
				ReplacerEnvironment env = new ReplacerEnvironment();
				env.add("path", path);
				return new CommandResponse(500, session.jprintf(_bundle,_keyPrefix+"approve.error.path", env, user));
			}
		} 
		
		if ((user != null) && (!dir.isRoot())) {
			ReplacerEnvironment env = new ReplacerEnvironment();
			env.add("path", dir.getPath());
	
			// Mark or remove dir as approved!
			try {
				if (remove) {
					dir.removePluginMetaData(Approve.APPROVE);
					return new CommandResponse(200, session.jprintf(_bundle,_keyPrefix+"approve.remove", env, user));
				}
				
				try {
					if (!dir.getPluginMetaData(Approve.APPROVE)) {
						throw new KeyNotFoundException();
					}
					return new CommandResponse(200, session.jprintf(_bundle,_keyPrefix+"approve.approved", env, user));
				} catch (KeyNotFoundException e) {
					dir.addPluginMetaData(Approve.APPROVE, true);
					return new CommandResponse(200, session.jprintf(_bundle,_keyPrefix+"approve.success", env, user));
				}
				
			} catch (FileNotFoundException e) {
                logger.error("Dir was just here but now its gone, {}", dir.getPath());
				return new CommandResponse(500, "Dir was just here but now its gone, " + dir.getPath());
			}			
		}
		return new CommandResponse(500, "Cannot Approve Directory");
	}
}
