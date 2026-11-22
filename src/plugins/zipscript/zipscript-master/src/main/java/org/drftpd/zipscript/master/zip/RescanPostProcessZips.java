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
package org.drftpd.zipscript.master.zip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.StandardCommandManager;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.Session;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip.DizReadResult;

import java.io.FileNotFoundException;
import java.util.Locale;

/**
 * @author djb61
 * @version $Id$
 */
public class RescanPostProcessZips extends ZipTools implements RescanPostProcessDirInterface {

    private static final Logger logger = LogManager.getLogger(RescanPostProcessZips.class);

    /**
     * What a rescan has to do with the cached DIZ info of a directory.
     */
    enum DizAction {
        /** DIZ info can not be refreshed right now (no slave to ask), keep the cached one. */
        KEEP,
        /** No valid DIZ was found in the zips, the DIZ is gone: drop the cached one. */
        DROP,
        /** A fresh valid DIZ was read: store it. */
        SET
    }

    static boolean isZip(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /**
     * Decides what to do with the cached DIZ info after the slaves were asked.
     */
    static DizAction decideResult(DizReadResult.Status status) {
        switch (status) {
            case FOUND:
                return DizAction.SET;
            case NOT_FOUND:
                return DizAction.DROP;
            default:
                return DizAction.KEEP;
        }
    }

    public void initialize(StandardCommandManager cManager) {
    }

    public void postProcessDir(CommandRequest workingDirReq, boolean quiet) {
        Session session = workingDirReq.getSession();
        DirectoryHandle dir = workingDirReq.getCurrentDirectory();
        boolean hasZips = false;
        try {
            for (FileHandle file : dir.getSortedFiles(
                    session.getUserNull(workingDirReq.getUser()))) {
                if (file.getSize() > 0 && isZip(file.getName())) {
                    hasZips = true;
                    try {
                        RemoteSlave rslave = file.getASlaveForFunction();
                        String index = ZipscriptVFSDataZip.getZipIssuer().issueZipCRCToSlave(rslave, file.getPath());
                        boolean ok = getZipIntegrityFromIndex(rslave, index);
                        if (ok) {
                            if (!quiet) {
                                session.printOutput(200, file.getName() + " - Zip integrity check OK");
                            }
                        } else {
                            session.printOutput(200, file.getName() + " - Zip integrity check failed, deleting file");
                            try {
                                file.deleteUnchecked();
                            } catch (FileNotFoundException e) {
                                // file disappeared, not a problem as we wanted it gone anyway
                            }
                        }
                    } catch (SlaveUnavailableException e) {
                        // okay, it went offline while trying
                        session.printOutput(200, file.getName() + " - Slave went offline whilst checking zip integrity");
                    } catch (RemoteIOException e) {
                        session.printOutput(200, file.getName() + " - Slave encountered an error whilst checking zip integrity");
                        logger.warn("Error encountered whilst checking zip integrity", e);
                    } catch (NoAvailableSlaveException e) {
                        session.printOutput(200, file.getName() + " - No available slave found to perform zip integrity check");
                    }
                }
            }
        } catch (FileNotFoundException e) {
            session.printOutput(200, dir.getName() + " - Directory deleted  whilst checking integrity of zips");
            return;
        }
        if (hasZips) {
            refreshDizInfo(session, dir, quiet);
        }
    }

    /**
     * Reads the DIZ info again from the slaves so that a corrected or late .diz is picked up. The cache is only
     * changed once the read is done: a valid DIZ replaces it, no valid DIZ drops it and, if no slave could be asked
     * (temporary), the previous DIZ info is kept.
     */
    private void refreshDizInfo(Session session, DirectoryHandle dir, boolean quiet) {
        DizReadResult result = new ZipscriptVFSDataZip(dir).readDizInfoFresh();
        try {
            switch (decideResult(result.getStatus())) {
                case SET:
                    dir.addPluginMetaData(DizInfo.DIZINFO, result.getDizInfo());
                    if (!quiet) {
                        session.printOutput(200, dir.getName() + " - DIZ info refreshed, expecting " + result.getDizInfo().getTotal() + " files");
                    }
                    break;
                case DROP:
                    dir.removePluginMetaData(DizInfo.DIZINFO);
                    if (!quiet) {
                        session.printOutput(200, dir.getName() + " - No usable DIZ info found");
                    }
                    break;
                default:
                    session.printOutput(200, dir.getName() + " - Unable to refresh DIZ info, keeping the previous one");
            }
        } catch (FileNotFoundException e) {
            // directory deleted meanwhile, nothing to update
            logger.debug("Directory {} gone while refreshing DIZ info", dir, e);
        }
    }

}
