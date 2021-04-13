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
package org.drftpd.zipscript.master.zip.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandManagerInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.dataconnection.DataConnectionHandler;
import org.drftpd.master.commands.dir.Dir;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.GroupPosition;
import org.drftpd.master.util.RankUtils;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.util.UploaderPosition;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.common.zip.DizStatus;
import org.drftpd.zipscript.master.zip.ZipTools;
import org.drftpd.zipscript.master.zip.event.ZipTransferEvent;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;


/**
 * @author djb61
 * @version $Id$
 */

public class ZipscriptZipPostHook extends ZipTools {

    private static final Logger logger = LogManager.getLogger(ZipscriptZipPostHook.class);

    private final ResourceBundle _bundle;

    public ZipscriptZipPostHook(CommandManagerInterface manager) {
        _bundle = manager.getResourceBundle();
    }

    @CommandHook(commands = "doSTOR", priority = 13, type = HookType.POST)
    public void doZipscriptSTORZipPostCheck(CommandRequest request, CommandResponse response) {
        // removing this in-case of a bad transfer
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        if (cfg.getProperty("stor.zip.integrity.check.enabled", "false").equalsIgnoreCase("true")) {
            FileHandle transferFile;
            try {
                transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
            } catch (KeyNotFoundException e) {
                // We don't have a file, we shouldn't have ended up here but return anyway
                return;
            }
            if (!transferFile.exists()) {
                // No point checking the file as it has already been deleted (i.e. an abort)
                return;
            }

            String transferFileName = transferFile.getName();
            try {
                if (transferFile.getSize() > 0 && transferFileName.toLowerCase().endsWith(".zip")) {
                    logger.debug("Running zipscript integrity check on stored file {}", transferFileName);
                    try {
                        RemoteSlave rslave = transferFile.getASlaveForFunction();
                        String index = ZipscriptVFSDataZip.getZipIssuer().issueZipCRCToSlave(rslave, transferFile.getPath());
                        boolean ok = getZipIntegrityFromIndex(rslave, index);
                        if (ok) {
                            response.addComment("Zip integrity check OK");
                            if (transferFile.exists()) {
                                try {
                                    BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
                                    RemoteSlave transferSlave = response.getObject(DataConnectionHandler.TRANSFER_SLAVE);
                                    InetAddress transferSlaveInetAddr =
                                            response.getObject(DataConnectionHandler.TRANSFER_SLAVE_INET_ADDRESS);
                                    char transferType = response.getObject(DataConnectionHandler.TRANSFER_TYPE);
                                    ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(request.getCurrentDirectory());
                                    GlobalContext.getEventService().publishAsync(
                                            new ZipTransferEvent(conn, "STOR", transferFile,
                                                    conn.getClientAddress(), transferSlave, transferSlaveInetAddr,
                                                    transferType, zipData, zipData.getDizInfo(), zipData.getDizStatus()));
                                } catch (KeyNotFoundException e1) {
                                    // one or more bits of information didn't get populated correctly, have to skip the event
                                } catch (IOException e) {
                                    // Do nothing, from user perspective STOR has completed so no point informing them
                                }
                            }
                        } else {
                            response.addComment("Zip integrity check failed, deleting file");
                            try {
                                transferFile.deleteUnchecked();
                            } catch (FileNotFoundException e) {
                                // file disappeared, not a problem as we wanted it gone anyway
                            }
                        }
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying
                        response.addComment("Slave went offline whilst checking zip integrity");
                    } catch (RemoteIOException e) {
                        response.addComment("Slave encountered an error whilst checking zip integrity");
                        logger.warn("Error encountered whilst checking zip integrity", e);
                    } catch (NoAvailableSlaveException e) {
                        response.addComment("No available slave found to perform zip integrity check");
                    }
                }
            } catch (FileNotFoundException e) {
                response.addComment("File has already been deleted, skipping zip integrity check");
            }
        }
    }

    @CommandHook(commands = "doSTOR", priority = 14, type = HookType.POST)
    public void doZipscriptSTORDizStatsHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
            // STOR failed, abort stats
            return;
        }
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        if (cfg.getProperty("stor.zip.racestats.enabled", "false").equalsIgnoreCase("true")) {
            addRaceStats(request, response, request.getCurrentDirectory());
        }
    }

    @CommandHook(commands = "doCWD", priority = 13, type = HookType.POST)
    public void doZipscriptCWDDizInfoHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // CWD failed, abort diz info
            return;
        }
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        if (cfg.getProperty("cwd.diz.info.enabled", "false").equalsIgnoreCase("true")) {
            try {
                ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(response.getCurrentDirectory());
                DizInfo dizInfo = zipData.getDizInfo();
                response.addComment(new String(Base64.getMimeDecoder().decode(dizInfo.getString()), StandardCharsets.ISO_8859_1));
            } catch (IOException | NoAvailableSlaveException e) {
                //Error fetching .diz, ignore
            }
        }
    }

    @CommandHook(commands = "doCWD", priority = 14, type = HookType.POST)
    public void doZipscriptCWDDizStatsHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // CWD failed, abort stats
            return;
        }
        Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf");
        if (cfg.getProperty("cwd.zip.racestats.enabled", "false").equalsIgnoreCase("true")) {
            addRaceStats(request, response, response.getCurrentDirectory());
        }
    }

    @CommandHook(commands = "doDELE", priority = 13, type = HookType.POST)
    public void doZipscriptDELEDizCleanupHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // DELE failed, abort info
            return;
        }
        String deleFileName;
        try {
            deleFileName = response.getObject(Dir.FILENAME);
        } catch (KeyNotFoundException e) {
            // We don't have a file, we shouldn't have ended up here but return anyway
            return;
        }
        if (deleFileName.toLowerCase().endsWith(".zip")) {
            try {
                boolean noZip = true;
                // Check if there are any other zips left
                for (FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
                    if (file.getName().toLowerCase().endsWith(".zip")) {
                        noZip = false;
                        break;
                    }
                }
                if (noZip) {
                    request.getCurrentDirectory().removePluginMetaData(DizInfo.DIZINFO);
                }
            } catch (FileNotFoundException e) {
                // No inode to remove dizinfo from or dir has been deleted
            }
        }
    }

    @CommandHook(commands = "doWIPE", priority = 13, type = HookType.POST)
    public void doZipscriptWIPEDizCleanupHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // WIPE failed, abort cleanup
            return;
        }
        String arg = request.getArgument();

        if (!arg.toLowerCase().endsWith(".zip")) {
            return;
        }

        DirectoryHandle wipeDir;
        try {
            wipeDir = request.getCurrentDirectory().getNonExistentFileHandle(response.getObject(Dir.WIPE_PATH)).getParent();
        } catch (KeyNotFoundException e) {
            return;
        }

        try {
            boolean noZip = true;
            // Check if there are any other zips left
            for (FileHandle file : wipeDir.getFilesUnchecked()) {
                if (file.getName().toLowerCase().endsWith(".zip")) {
                    noZip = false;
                    break;
                }
            }
            if (noZip) {
                wipeDir.removePluginMetaData(DizInfo.DIZINFO);
            }
        } catch (FileNotFoundException e) {
            // No inode to remove dizinfo from or dir has been deleted
        }
    }

    private void addRaceStats(CommandRequest request, CommandResponse response, DirectoryHandle dir) {
        // show race stats
        try {
            ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);
            DizInfo dizInfo = zipData.getDizInfo();
            DizStatus dizStatus = zipData.getDizStatus();

            Collection<UploaderPosition> racers = RankUtils.userSort(getZipFiles(dir),
                    "bytes", "high");
            Collection<GroupPosition> groups = RankUtils.topFileGroup(getZipFiles(dir));

            String racerline = _bundle.getString("cwd.racers.body");
            String groupline = _bundle.getString("cwd.groups.body");

            Map<String, Object> env = request.getSession().getReplacerEnvironment(null,
                    request.getSession().getUserNull(request.getUser()));

            //Start building race message
            StringBuilder raceTextBuilder = new StringBuilder(_bundle.getString("cwd.racestats.header"));
            raceTextBuilder.append('\n');
            raceTextBuilder.append(_bundle.getString("cwd.racers.header"));
            raceTextBuilder.append('\n');

            //Add racer stats
            int position = 1;

            for (UploaderPosition stat : racers) {
                User raceuser;

                try {
                    raceuser = GlobalContext.getGlobalContext().getUserManager().getUserByName(stat.getUsername());
                } catch (NoSuchUserException e2) {
                    continue;
                } catch (UserFileException e2) {
                    logger.error("Error reading userfile", e2);
                    continue;
                }

                Map<String, Object> raceenv = new HashMap<>();
                raceenv.put("speed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
                raceenv.put("user", stat.getUsername());
                raceenv.put("group", raceuser.getGroup());
                raceenv.put("files", "" + stat.getFiles());
                raceenv.put("bytes", Bytes.formatBytes(stat.getBytes()));
                raceenv.put("position", String.valueOf(position));
                raceenv.put("percent", (stat.getFiles() * 100) / dizInfo.getTotal() + "%");

                raceTextBuilder.append(ReplacerUtils.jprintf(racerline, raceenv));
                raceTextBuilder.append('\n');
                position++;
            }

            raceTextBuilder.append(_bundle.getString("cwd.racers.footer"));
            raceTextBuilder.append('\n');
            raceTextBuilder.append(_bundle.getString("cwd.groups.header"));
            raceTextBuilder.append('\n');

            //add groups stats
            position = 1;

            for (GroupPosition stat : groups) {
                Map<String, Object> raceenv = new HashMap<>();
                raceenv.put("group", stat.getGroupname());
                raceenv.put("position", String.valueOf(position));
                raceenv.put("bytes", Bytes.formatBytes(stat.getBytes()));
                raceenv.put("files", Integer.toString(stat.getFiles()));
                raceenv.put("percent", (stat.getFiles() * 100) / dizInfo.getTotal() + "%");
                raceenv.put("speed", Bytes.formatBytes(stat.getXferspeed()) + "/s");

                raceTextBuilder.append(ReplacerUtils.jprintf(groupline, raceenv));
                raceTextBuilder.append('\n');
                position++;
            }

            raceTextBuilder.append(_bundle.getString("cwd.groups.footer"));
            raceTextBuilder.append('\n');

            env.put("completefiles", dizStatus.getPresent() + "/" + dizInfo.getTotal());
            env.put("totalbytes", Bytes.formatBytes(getZipTotalBytes(dir)));
            env.put("totalspeed", Bytes.formatBytes(getXferspeed(dir)) + "/s");
            env.put("totalpercent", (getZipFiles(dir).size() * 100) / dizInfo.getTotal() + "%");

            raceTextBuilder.append(_bundle.getString("cwd.totals.body"));
            raceTextBuilder.append('\n');
            raceTextBuilder.append(_bundle.getString("cwd.racestats.footer"));
            raceTextBuilder.append('\n');

            response.addComment(ReplacerUtils.jprintf(raceTextBuilder.toString(), env));
        } catch (IOException | NoAvailableSlaveException e) {
            //Error fetching SFV, ignore
        }
    }
}
