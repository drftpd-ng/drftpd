/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.dupecheck.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.dupecheck.master.metadata.DupeCheckFileData;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.config.ConfigInterface;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.indexation.AdvancedSearchParams;
import org.drftpd.master.indexation.IndexEngineInterface;
import org.drftpd.master.indexation.IndexException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.master.vfs.VirtualFileSystem;

import java.io.FileNotFoundException;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * @author CyBeR
 * @version $Id: DupeCheck.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class DupeCheckHooks {

    private static final Logger logger = LogManager.getLogger(DupeCheckHooks.class);

    private Pattern _exempt = null;

    private int _type = 0;

    public DupeCheckHooks() {
        loadConf();
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        loadConf();
    }

    /*
     * Loads config from file and sets regex patterns
     */
    private void loadConf() {
        Properties cfg = ConfigLoader.loadPluginConfig("dupecheck.conf");
        String exempt = cfg.getProperty("exempt");
        String type = cfg.getProperty("type");

        if (exempt != null) {
            _exempt = null;
            try {
                _exempt = Pattern.compile(exempt.trim());
                logger.debug("Exempt pattern parsed and compiled succesffull [{}]", _exempt.toString());
            } catch(PatternSyntaxException pse) {
                logger.error("Provided exempt pattern is incorrectly formatted", pse);
            }
        }

        if (type != null) {
            _type = Integer.parseInt(type.trim());
            if (_type < 0 || _type > 3) {
                logger.error("Incorrect type {} found, ignoring and disabling dupechecking", type);
                _type = 0;
            }
        }
        logger.info("Dupe checking type has been set to {}", _type);
    }

    /*
     * Prehook method for Making a New DIR
     */
    @CommandHook(commands = "doMKD", type = HookType.PRE)
    public CommandRequestInterface doDupeCheckMKD(CommandRequest request) {
        logger.debug("[doDupeCheckMKD] pre hook for doMKD started");
        if (request.hasArgument()) {
            String dirname = request.getCurrentDirectory().getNonExistentDirectoryHandle(VirtualFileSystem.fixPath(request.getArgument())).getName();
            if (_type == 2 || _type == 3) {
                logger.debug("Dir based dupe checking is enabled, checking for exempt before doing dupe check");
                if (checkFile(dirname)) {
                    return doDupeCheck(request, dirname, "doDupeCheckMKD");
                }
            }
        }
        return request;
    }

    /*
     * Prehook method for Creating a New FILE
     */
    @CommandHook(commands = "doSTOR", type = HookType.PRE)
    public CommandRequestInterface doDupeCheckSTOR(CommandRequest request) {
        logger.debug("[doDupeCheckSTOR] pre hook for doSTOR started");
        if (request.hasArgument()) {
            String filename = request.getCurrentDirectory().getNonExistentFileHandle(VirtualFileSystem.fixPath(request.getArgument())).getName();
            if(_type == 1 || _type == 3) {
                logger.debug("File based dupe checking is enabled, checking for exempt before doing dupe check");
                if (checkFile(filename)) {
                    return doDupeCheck(request, filename, "doDupeCheckSTOR");
                }
            }
        }
        return request;
    }

    /*
     * Checks if the file should be dupe checked
     *
     * Returns false if the file is exempted and thus should not be checked
     */
    private boolean checkFile(String file) {
        if (_exempt != null) {
            Matcher matcher = _exempt.matcher(file.toLowerCase());
            return !matcher.find();
        }
        return true;
    }

    /*
     * Checks to see if file/dir is already in database
     * Then checks to see if user has permission to upload file/dir
     */
    private CommandRequestInterface doDupeCheck(CommandRequest request, String realname, String caller) {

        logger.debug("[doDupeCheck] Dupe checking {}", realname);

        try {
            User user = request.getSession().getUserNull(request.getUser());

            AdvancedSearchParams params = new AdvancedSearchParams();
            params.setExact(realname);

            IndexEngineInterface ie = GlobalContext.getGlobalContext().getIndexEngine();
            Map<String, String> inodes = ie.advancedFind(GlobalContext.getGlobalContext().getRoot(), params, caller);

            logger.debug("From the index we found {} items to check", inodes.size());

            if (!inodes.isEmpty()) {
                for (Map.Entry<String, String> item : inodes.entrySet()) {
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
                        logger.warn("Index Contained an nonexistent inode: {}", item.getKey());
                    }

                    if (isDupe) {
                        ConfigInterface config = GlobalContext.getConfig();
                        if (config.checkPathPermission("dupecheck", user, request.getCurrentDirectory())) {
                            request.setDeniedResponse(new CommandResponse(400, "DUPE: " + inode.getPath()));
                            request.setAllowed(false);
                            return request;
                        }
                    }
                }
            }

        } catch (IndexException e) {
            // Index Exception while searching
            logger.error("While dupe checking we received an index error: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            // Invalid Argument while searching
            logger.error("While dupe checking we received a illegal argument error: {}", e.getMessage());
        }
        return request;
    }
}
