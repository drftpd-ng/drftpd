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

import java.io.FileNotFoundException;
import java.io.IOException;
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
        /** Directory has no zips, leave the DIZ info alone. */
        NONE,
        /** DIZ info can not be refreshed right now (no slave to ask), keep the cached one. */
        KEEP,
        /** Try to read the DIZ info again from a slave. */
        REFRESH,
        /** Every zip was read but no valid DIZ was found, the DIZ is gone: leave it dropped. */
        DROP,
        /** A fresh valid DIZ was read and stored. */
        SET
    }

    /**
     * Outcome of reading the DIZ info from the slaves.
     */
    enum DizReadResult {
        /** A valid DIZ was found. */
        FOUND,
        /** Slaves were asked but no valid DIZ was found. */
        NOT_FOUND,
        /** No slave could be asked (temporary failure). */
        UNAVAILABLE
    }

    static boolean isZip(String name) {
        return name.toLowerCase(Locale.ROOT).endsWith(".zip");
    }

    /**
     * Decides whether the DIZ info should be refreshed at all.
     * A refresh is only attempted if there are zips and at least one of them has an available slave, otherwise a
     * failure to read the DIZ would be indistinguishable from a DIZ that really is gone.
     */
    static DizAction decideRefresh(boolean hasZips, boolean anySlaveAvailable) {
        if (!hasZips) {
            return DizAction.NONE;
        }
        return anySlaveAvailable ? DizAction.REFRESH : DizAction.KEEP;
    }

    /**
     * Decides what to do with the cached DIZ info after the slaves were asked.
     */
    static DizAction decideResult(DizReadResult result) {
        switch (result) {
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
        DizAction action = decideRefresh(hasZips, hasZips && hasAvailableZipSlave(session, dir, workingDirReq));
        if (action == DizAction.REFRESH) {
            refreshDizInfo(session, dir, quiet);
        } else if (action == DizAction.KEEP) {
            session.printOutput(200, dir.getName() + " - Unable to refresh DIZ info, keeping the previous one");
        }
    }

    /**
     * @return true if at least one zip of the directory is on a slave that is currently online
     */
    private boolean hasAvailableZipSlave(Session session, DirectoryHandle dir, CommandRequest workingDirReq) {
        try {
            for (FileHandle file : dir.getSortedFiles(session.getUserNull(workingDirReq.getUser()))) {
                if (file.getSize() > 0 && isZip(file.getName())) {
                    try {
                        file.getAvailableSlaves();
                        return true;
                    } catch (NoAvailableSlaveException e) {
                        // try the next zip
                    }
                }
            }
        } catch (FileNotFoundException e) {
            // directory or file gone, nothing to ask
        }
        return false;
    }

    /**
     * Drops the cached DIZ info of the directory and retrieves it again so that a corrected or late .diz is picked up.
     * Only called when at least one slave can be asked: if the zips are read but no valid DIZ is found, the DIZ is
     * really gone and stays dropped. If no slave can be asked (temporary), the previous DIZ info is put back.
     */
    private void refreshDizInfo(Session session, DirectoryHandle dir, boolean quiet) {
        DizInfo previous;
        try {
            previous = dir.removePluginMetaData(DizInfo.DIZINFO);
        } catch (FileNotFoundException e) {
            return;
        }
        DizReadResult result;
        DizInfo dizInfo = null;
        try {
            dizInfo = new ZipscriptVFSDataZip(dir).getDizInfo();
            result = DizReadResult.FOUND;
        } catch (FileNotFoundException e) {
            logger.debug("No usable DIZ info found in {} while rescanning", dir, e);
            result = DizReadResult.NOT_FOUND;
        } catch (IOException | NoAvailableSlaveException e) {
            logger.warn("Unable to refresh DIZ info for {} while rescanning", dir, e);
            result = DizReadResult.UNAVAILABLE;
        }
        switch (decideResult(result)) {
            case SET:
                if (!quiet) {
                    session.printOutput(200, dir.getName() + " - DIZ info refreshed, expecting " + dizInfo.getTotal() + " files");
                }
                break;
            case DROP:
                if (!quiet) {
                    session.printOutput(200, dir.getName() + " - No usable DIZ info found");
                }
                break;
            default:
                restoreDizInfo(dir, previous);
                session.printOutput(200, dir.getName() + " - Unable to refresh DIZ info, keeping the previous one");
        }
    }

    private void restoreDizInfo(DirectoryHandle dir, DizInfo previous) {
        if (previous == null) {
            return;
        }
        try {
            dir.addPluginMetaData(DizInfo.DIZINFO, previous);
        } catch (FileNotFoundException e) {
            // directory deleted meanwhile, nothing to restore
        }
    }

}
