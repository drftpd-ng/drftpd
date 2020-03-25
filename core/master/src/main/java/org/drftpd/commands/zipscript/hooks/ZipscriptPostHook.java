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
package org.drftpd.commands.zipscript.hooks;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.event.SFVMemberTransferEvent;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.common.CommandHook;
import org.drftpd.common.HookType;
import org.drftpd.master.Checksum;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.RankUtils;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.master.BaseFtpConnection;
import org.drftpd.master.master.ConnectionManager;
import org.drftpd.master.master.RemoteSlave;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.util.GroupPosition;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.util.UploaderPosition;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.plugins.commandmanager.CommandRequest;
import org.drftpd.plugins.commandmanager.CommandResponse;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.util.*;

import static org.drftpd.master.GlobalContext.getGlobalContext;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptPostHook extends SFVTools {

    private static final Logger logger = LogManager.getLogger(ZipscriptPostHook.class);
    private ResourceBundle _bundle;

    public ZipscriptPostHook() {
        _bundle = ConnectionManager.getConnectionManager().getCommandManager().getResourceBundle();
    }

    @CommandHook(commands = "doRETR", priority = 10, type = HookType.POST)
    public void doZipscriptRETRPostCheck(CommandRequest request, CommandResponse response) {

        if (response.getCode() != 226) {
            // Transfer failed, abort checks
            return;
        }
        FileHandle transferFile;
        try {
            transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
        } catch (KeyNotFoundException e) {
            // We don't have a file, we shouldn't have ended up here but return anyway
            return;
        }

        String transferFileName = transferFile.getName();
        long checksum = response.getObjectLong(DataConnectionHandler.CHECKSUM);
        logger.debug("Running zipscript on retrieved file {} with CRC of {}", transferFileName, checksum);

        if (checksum != 0) {
            response.addComment("Checksum from transfer: " +
                    Checksum.formatChecksum(checksum));

            //compare checksum from transfer to checksum from sfv
            try {
                ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(transferFile.getParent());
                SFVInfo sfv = sfvData.getSFVInfo();
                Long sfvChecksum = sfv.getEntries().get(transferFile.getName());

                if (sfvChecksum == null) {
                    // No entry in sfv for this file so nothing to check against
                } else if (checksum == sfvChecksum) {
                    response.addComment(
                            "checksum from transfer matched checksum in .sfv");
                } else {
                    response.addComment(
                            "WARNING: checksum from transfer didn't match checksum in .sfv");
                }
            } catch (NoAvailableSlaveException e1) {
                response.addComment(
                        "slave with .sfv offline, checksum not verified");
            } catch (FileNotFoundException e1) {
                //continue without verification
            } catch (IOException e1) {
                //continue without verification
            } catch (SlaveUnavailableException e1) {
                response.addComment(
                        "slave with .sfv offline, checksum not verified");
            }
        } else { // slave has disabled download crc

            //response.addComment("Slave has disabled download checksum");
        }
    }

    @CommandHook(commands = "doSTOR", priority = 10, type = HookType.POST)
    public void doZipscriptSTORPostCheck(CommandRequest request, CommandResponse response) {
        // removing this in-case of a bad transfer
		/*if (response.getCode() != 226) {
			// Transfer failed, abort checks
			return;
		}*/
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
        long checksum = response.getObjectLong(DataConnectionHandler.CHECKSUM);
        logger.debug("Running zipscript on stored file {} with CRC of {}", transferFileName, Checksum.formatChecksum(checksum));
        if (!transferFileName.toLowerCase().endsWith(".sfv")) {
            try {
                ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(transferFile.getParent());
                SFVInfo sfv = sfvData.getSFVInfo();
                Long sfvChecksum = sfv.getEntries().get(transferFile.getName());

                /*If no exceptions are thrown means that the sfv is available and has a entry
                 * for that file.
                 * With this certain, we can assume that files that have CRC32 = 0 either is a
                 * 0byte file (bug!) or checksummed transfers are disabled(size is different
                 * from 0bytes though).
                 */
                if (sfvChecksum == null) {
                    // No entry in the sfv for this file, just return and allow
                    response.addComment("zipscript - no entry in sfv for file");
                } else if (checksum == sfvChecksum) {
                    // Good! transfer checksum matches sfv checksum
                    response.addComment("checksum match: SLAVE/SFV:" +
                            Checksum.formatChecksum(checksum));
                    if (transferFile.exists()) {
                        try {
                            BaseFtpConnection conn = (BaseFtpConnection) request.getSession();
                            RemoteSlave transferSlave = response.getObject(DataConnectionHandler.TRANSFER_SLAVE);
                            InetAddress transferSlaveInetAddr =
                                    response.getObject(DataConnectionHandler.TRANSFER_SLAVE_INET_ADDRESS);
                            char transferType = response.getObject(DataConnectionHandler.TRANSFER_TYPE);
                            GlobalContext.getEventService().publishAsync(
                                    new SFVMemberTransferEvent(conn, "STOR", transferFile,
                                            conn.getClientAddress(), transferSlave, transferSlaveInetAddr,
                                            transferType, sfvData, sfv, sfvData.getSFVStatus()));
                        } catch (KeyNotFoundException e1) {
                            // one or more bits of information didn't get populated correctly, have to skip the event
                        }
                    }
                } else if (checksum == 0) {
                    // Here we have two conditions:
                    if (transferFile.getSize() == 0) {
                        // The file has checksum = 0 and the size = 0
                        // then it should be deleted.
                        logger.debug("0Byte File, Deleting...");
                        response.addComment("0Byte File, Deleting...");
                        transferFile.deleteUnchecked();
                    } else
                        // The file has checksum = 0, although the size is != 0,
                        // meaning that we are not using checked transfers.
                        response.addComment("checksum match: SLAVE/SFV: DISABLED");
                } else {
                    logger.debug("checksum mismatch: SLAVE: {} SFV: {} - deleting file", Checksum.formatChecksum(checksum), Checksum.formatChecksum(sfvChecksum));
                    response.addComment("checksum mismatch: SLAVE: " +
                            Checksum.formatChecksum(checksum) + " SFV: " +
                            Checksum.formatChecksum(sfvChecksum));
                    response.addComment(" deleting file");
                    response.setMessage("Checksum mismatch, deleting file");
                    transferFile.deleteUnchecked();
                }
            } catch (NoAvailableSlaveException e) {
                response.addComment(
                        "zipscript - SFV unavailable, slave(s) with .sfv file is offline");
            } catch (FileNotFoundException e) {
                // No sfv found in parent dir for this file, skip check
            } catch (IOException e) {
                response.addComment(
                        "zipscript - SFV unavailable, IO error: " +
                                e.getMessage());
            } catch (SlaveUnavailableException e) {
                response.addComment(
                        "zipscript - SFV unavailable, slave(s) with .sfv file is offline");
            }
        }
    }

    @CommandHook(commands = "doCWD", priority = 10, type = HookType.POST)
    public void doZipscriptCWDStatsHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // CWD failed, abort stats
            return;
        }
        Properties cfg = getGlobalContext().getPluginsConfig().
                getPropertiesForPlugin("zipscript.conf");
        if (cfg.getProperty("cwd.racestats.enabled", "false").equalsIgnoreCase("true")) {
            addRaceStats(request, response, response.getCurrentDirectory());
        }
    }

    @CommandHook(commands = "doSTOR", priority = 11, type = HookType.POST)
    public void doZipscriptSTORStatsHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
            // STOR failed, abort stats
            return;
        }
        Properties cfg = getGlobalContext().getPluginsConfig().
                getPropertiesForPlugin("zipscript.conf");
        if (cfg.getProperty("stor.racestats.enabled", "false").equalsIgnoreCase("true")) {
            addRaceStats(request, response, request.getCurrentDirectory());
        }
    }

    @CommandHook(commands = "doDELE", priority = 10, type = HookType.POST)
    public void doZipscriptDELECleanupHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // DELE failed, abort cleanup
            return;
        }
        String deleFileName;
        try {
            deleFileName = response.getObject(Dir.FILENAME);
        } catch (KeyNotFoundException e) {
            // We don't have a file, we shouldn't have ended up here but return anyway
            return;
        }
        if (deleFileName.toLowerCase().endsWith(".sfv")) {
            try {
                request.getCurrentDirectory().removePluginMetaData(SFVInfo.SFVINFO);
            } catch (FileNotFoundException e) {
                // No inode to remove sfvinfo from
            }
        }
    }

    private void addRaceStats(CommandRequest request, CommandResponse response, DirectoryHandle dir) {
        // show race stats
        try {
            ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
            SFVInfo sfvInfo = sfvData.getSFVInfo();
            SFVStatus sfvStatus = sfvData.getSFVStatus();
            Collection<UploaderPosition> racers = RankUtils.userSort(getSFVFiles(dir, sfvData),
                    "bytes", "high");
            Collection<GroupPosition> groups = RankUtils.topFileGroup(getSFVFiles(dir, sfvData));

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
                    raceuser = getGlobalContext().getUserManager()
                            .getUserByName(stat.getUsername());
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
                raceenv.put("percent", (stat.getFiles() * 100) / sfvInfo.getSize() + "%");

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
                raceenv.put("percent", (stat.getFiles() * 100) / sfvInfo.getSize() + "%");
                raceenv.put("speed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
				raceTextBuilder.append(ReplacerUtils.jprintf(groupline, raceenv));
				raceTextBuilder.append('\n');
				position++;
            }

            raceTextBuilder.append(_bundle.getString("cwd.groups.footer"));
            raceTextBuilder.append('\n');

            env.put("completefiles", sfvStatus.getPresent() + "/" + sfvInfo.getSize());
            env.put("totalbytes", Bytes.formatBytes(getSFVTotalBytes(dir, sfvData)));
            env.put("totalspeed", Bytes.formatBytes(getXferspeed(dir, sfvData)) + "/s");
            env.put("totalpercent", (sfvStatus.getPresent() * 100) / sfvInfo.getSize() + "%");

            raceTextBuilder.append(_bundle.getString("cwd.totals.body"));
            raceTextBuilder.append('\n');
            raceTextBuilder.append(_bundle.getString("cwd.racestats.footer"));
            raceTextBuilder.append('\n');

			response.addComment(ReplacerUtils.jprintf(raceTextBuilder.toString(), env));

        } catch (SlaveUnavailableException | NoAvailableSlaveException | IOException e) {
            //Error fetching SFV, ignore
        }
    }
}
