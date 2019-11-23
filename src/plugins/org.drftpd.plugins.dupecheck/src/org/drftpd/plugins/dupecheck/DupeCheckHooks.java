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
package org.drftpd.plugins.dupecheck;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.*;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.event.ReloadEvent;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.plugins.dupecheck.metadata.DupeCheckFileData;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.drftpd.vfs.index.AdvancedSearchParams;
import org.drftpd.vfs.index.IndexEngineInterface;
import org.drftpd.vfs.index.IndexException;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author CyBeR
 * @version $Id: DupeCheck.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class DupeCheckHooks implements PreHookInterface {
	private static final Logger logger = LogManager.getLogger(DupeCheckHooks.class);

	private Pattern _exempt = null;
	private int _type = 0;
	
	public void initialize(StandardCommandManager cManager) {
		loadConf();
	}
	
    @EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
    	loadConf();
    }
	
    /*
     * Loads config from file and sets regex patterns
     */
	private void loadConf() {
    	Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("dupecheck.conf");
        
    	String exempt = cfg.getProperty("exempt");
        String type = cfg.getProperty("type");
        
        if (exempt != null) {
        	_exempt = null;
        	_exempt = Pattern.compile(exempt.trim());
        }

        if (type != null) {
        	_type = Integer.parseInt(type.trim());
        }        
    	
	}

	/*
	 * Prehook method for Making a New DIR
	 */
	public CommandRequestInterface doDupeCheckMKD(CommandRequest request) {
		if (request.hasArgument()) {
			String dirname = request.getCurrentDirectory().getNonExistentDirectoryHandle(VirtualFileSystem.fixPath(request.getArgument())).getName();
			if (((_type == 2) || (_type == 3)) && (!checkFile(dirname))) {
				return doDupeCheck(request,dirname);
			}
		}
		return request;
	}

	/*
	 * Prehook method for Creating a New FILE
	 */
	public CommandRequestInterface doDupeCheckSTOR(CommandRequest request) {
		if (request.hasArgument()) {
			String filename = request.getCurrentDirectory().getNonExistentFileHandle(VirtualFileSystem.fixPath(request.getArgument())).getName();
			if (((_type == 1) || (_type == 3)) && (!checkFile(filename))) {
				return doDupeCheck(request,filename);
			}
		}
		return request;
	}	
	
	/*
	 * Checks the file/dir to see if its in the regex exclude list
	 */
	private boolean checkFile(String file) {
		if (_exempt != null) {
			Matcher matcher = _exempt.matcher(file.toLowerCase());
            return matcher.find();
		}
		return false;
	}
	
	/*
	 * Checks to see if file/dir is already in database
	 * Then checks to see if user has permission to upload file/dir 
	 */
	private CommandRequestInterface doDupeCheck(CommandRequest request, String realname) {

		try {
			User user = request.getSession().getUserNull(request.getUser());

			AdvancedSearchParams params = new AdvancedSearchParams();
			params.setExact(realname);
			
			IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
			Map<String,String> inodes = ie.advancedFind(GlobalContext.getGlobalContext().getRoot(), params);
			
			if (!inodes.isEmpty()) {
				for (Map.Entry<String,String> item : inodes.entrySet()) {
					boolean isDupe = false;

					InodeHandle inode = item.getValue().equals("d") ? new DirectoryHandle(item.getKey()) : new FileHandle(item.getKey());					
					
					try {
						if (inode.getPluginMetaData(DupeCheckFileData.DUPE)) {
							isDupe = true;
						}
					} catch (KeyNotFoundException e) {
						// This is fine, means file/dir has not been un-duped
						isDupe = true;
					} catch (FileNotFoundException e) {
						// File not found, not good, index refers to file that does not exist
                        logger.warn("Index Contained an unexistent inode: {}", item.getKey());
					}
					
					if (isDupe) {
				        ConfigInterface config = GlobalContext.getConfig();
						if (config.checkPathPermission("dupecheck", user, request.getCurrentDirectory())) {
							request.setDeniedResponse(new CommandResponse(400,"DUPE: " + inode.getPath()));
							request.setAllowed(false);
							return request;
						}							
					}
				}
			}				
			
		} catch (IndexException e) {
			//Index Exception while searching
			logger.error(e.getMessage());
		} catch (IllegalArgumentException e) {
			//Invalid Arguement while searching
			logger.info(e.getMessage());
		}
		return request;
	}

	
}
