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
package org.drftpd.zipscript.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.master.commands.*;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Checksum;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.*;
import org.drftpd.zipscript.common.sfv.SFVInfo;
import org.drftpd.zipscript.master.sfv.vfs.ZipscriptVFSDataSFV;
import org.drftpd.zipscript.master.zip.RescanPostProcessDirInterface;
import org.reflections.Reflections;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptCommands extends CommandInterface {

    private static final Logger logger = LogManager.getLogger(ZipscriptCommands.class);

    private final ArrayList<RescanPostProcessDirInterface> _rescanAddons = new ArrayList<>();

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);

        // Subscribe to events
        AnnotationProcessor.process(this);

        Set<Class<? extends RescanPostProcessDirInterface>> rescanProcesses = new Reflections("org.drftpd")
                .getSubTypesOf(RescanPostProcessDirInterface.class);
        try {
            for (Class<? extends RescanPostProcessDirInterface> rescanProcess : rescanProcesses) {
                RescanPostProcessDirInterface anInterface = rescanProcess.getConstructor().newInstance();
                anInterface.initialize(cManager);
                _rescanAddons.add(anInterface);
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for org.drftpd.master.commands.zipscript extension point 'RescanPostProcessDir'" +
                    ", possibly the org.drftpd.master.commands.zipscript extension point definition has changed in the plugin.xml", e);
        }
    }

    public CommandResponse doSITE_RESCAN(CommandRequest request) throws ImproperUsageException {

        Session session = request.getSession();
        User user = session.getUserNull(request.getUser());

        String fileOrPath = null;
        boolean recursive = false;
        boolean forceRescan = true;
        boolean deleteBad = true;
        boolean deleteZeroByte = true;
        boolean quiet = false;
        boolean singleFile = false;

        StringTokenizer args = new StringTokenizer(request.getArgument());
        while (args.hasMoreTokens()) {
            String arg = args.nextToken();
            if (arg.equalsIgnoreCase("-r")) {
                recursive = true;
            } else if (arg.equalsIgnoreCase("noforce")) {
                forceRescan = false;
            } else if (arg.equalsIgnoreCase("nodelete")) {
                deleteBad = false;
            } else if (arg.equalsIgnoreCase("nodelete0byte")) {
                deleteZeroByte = false;
            } else if (arg.equalsIgnoreCase("quiet")) {
                quiet = true;
            } else {
                if (fileOrPath != null) {
                    throw new ImproperUsageException();
                }
                if (arg.startsWith(VirtualFileSystem.separator)) {
                    fileOrPath = arg;
                } else {
                    if (session instanceof BaseFtpConnection) {
                        fileOrPath = ((BaseFtpConnection) session).getCurrentDirectory().getPath() + VirtualFileSystem.separator + arg;
                    } else {
                        throw new ImproperUsageException();
                    }
                }
            }
        }

        if (!deleteBad && !deleteZeroByte) {
            logger.debug("Disabling forceRescan option as delete bad and zerobyte is not enabled");
            forceRescan = false;
        }
        logger.debug("Rescan options -> recursive: {}, force: {}, deleteBad: {}, deleteZeroByte: {}, quiet: {}, FileOrPath: {}",
                recursive, forceRescan, deleteBad, deleteZeroByte, quiet, fileOrPath);

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");

        LinkedList<DirectoryHandle> dirs = new LinkedList<>();
        if (fileOrPath != null) {
            boolean valid = false;
            try {
                if (InodeHandle.isDirectory(fileOrPath)) {
                    dirs.add(new DirectoryHandle(fileOrPath));
                    valid = true;
                }
                if (InodeHandle.isFile(fileOrPath)) {
                    if (recursive) {
                        return new CommandResponse(500, "Not possible to recursively work on a single file");
                    }
                    singleFile = true;
                    valid = true;
                }
            } catch (FileNotFoundException e) {
                logger.debug("Caught FileNotFoundException while checking for file or directory", e);
                // Do nothing, the valid path check will deal with this
            }
            if (!valid) {
                return new CommandResponse(500, fileOrPath + " is not a valid file or directory");
            }
        } else {
            logger.debug("Adding current working directory ({}) for rescan", request.getCurrentDirectory());
            dirs.add(request.getCurrentDirectory());
        }
        if (singleFile) {
            FileHandle workingFile = new FileHandle(fileOrPath);
            // Make sure the file exists!
            if (!workingFile.exists()) {
                return new CommandResponse(500, "File (" + fileOrPath + ") does not exist");
            }
            DirectoryHandle workingDir = workingFile.getParent();
            SFVInfo workingSFV = null;
            try {
                workingSFV = getSFVInfo(workingDir);
            } catch (NoAvailableSlaveException | SlaveUnavailableException e) {
                session.printOutput(200, "No available slave with sfv for: " + workingDir.getPath());
            }
            if (workingSFV == null) {
                logger.warn("Unable to get SFVInfo from: {}", workingDir.getPath());
                session.printOutput(200, "Unable to obtain SFVInfo, cannot do anything");
            } else {
                session.printOutput(200, "Rescanning single file (" + workingFile.getName() + ") in: " + workingDir.getPath());
                Long sfvChecksum = workingSFV.getEntries().get(workingFile.getName());
                if (sfvChecksum == null) {
                    session.printOutput(200, "File (" + workingFile.getName() + ") does not exist in sfv");
                } else {
                    long fileChecksum;
                    try {
                        fileChecksum = getFileCheckSum(workingFile, forceRescan);
                        String status = checkSingleFile(session, workingFile.getName(), workingFile, sfvChecksum, fileChecksum, deleteZeroByte, deleteBad, quiet);
                        if (status != null && !status.equals("")) {
                            session.printOutput(200, workingFile.getName() + "(" + status + ")" +
                                    " SFV: " + Checksum.formatChecksum(sfvChecksum) +
                                    " SLAVE: " + Checksum.formatChecksum(fileChecksum));
                        }
                    } catch (FileNotFoundException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + workingFile.getName() + " MISSING");
                    } catch (NoAvailableSlaveException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + workingFile.getName() + " OFFLINE");
                    }

                    // Run any post processing extensions
                    doRescanPostProcess(request, workingDir, quiet);
                }
            }
        } else {
            logger.debug("Before we start looping dirs list contains {} entries", dirs.size());
            while (dirs.size() > 0 && !session.isAborted()) {
                DirectoryHandle workingDir = dirs.poll();
                if (workingDir == null) {
                    logger.error("Found a 'null' entry after polling the directories, skipping");
                    continue;
                }
                if (recursive) {
                    try {
                        int orgSize = dirs.size();
                        dirs.addAll(workingDir.getSortedDirectories(user));
                        logger.debug("Added {} entries as we need to do recursive", (dirs.size() - orgSize));
                    } catch (FileNotFoundException e1) {
                        logger.error("Error recursively listing: {}", workingDir.getPath(), e1);
                        session.printOutput(200, "Error recursively listing: " + workingDir.getPath());
                    }
                }
                SFVInfo workingSFV = null;
                try {
                    workingSFV = getSFVInfo(workingDir);
                } catch (NoAvailableSlaveException | SlaveUnavailableException e) {
                    session.printOutput(200, "No available slave with sfv for: " + workingDir.getPath());
                }
                if (workingSFV == null) {
                    logger.warn("Unable to get SFVInfo from: {}", workingDir.getPath());
                    continue;
                }

                session.printOutput(200, "Rescanning: " + workingDir.getPath());
                for (Entry<String, Long> sfvEntry : workingSFV.getEntries().entrySet()) {
                    if (session.isAborted()) {
                        logger.warn("Breaking this rescan as the session is aborted");
                        break;
                    }
                    FileHandle file;
                    long sfvChecksum = sfvEntry.getValue();
                    String sfvEntryName = sfvEntry.getKey();
                    try {
                        file = workingDir.getFile(sfvEntryName, user);
                    } catch (FileNotFoundException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + sfvEntryName + " MISSING");
                        continue;
                    } catch (ObjectNotValidException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + sfvEntryName + " INVALID VFS ENTRY");
                        logger.error("Type error found in VFS, expected file {} and found something else", sfvEntryName, e);
                        continue;
                    }

                    long fileChecksum;
                    try {
                        fileChecksum = getFileCheckSum(file, forceRescan);
                    } catch (FileNotFoundException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + sfvEntryName + " MISSING");
                        continue;
                    } catch (NoAvailableSlaveException e) {
                        session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + sfvEntryName + " OFFLINE");
                        continue;
                    }

                    String status = checkSingleFile(session, sfvEntryName, file, sfvChecksum, fileChecksum, deleteZeroByte, deleteBad, quiet);
                    if (status != null && !status.equals("")) {
                        session.printOutput(200, file.getName() + "(" + status + ")" +
                                " SFV: " + Checksum.formatChecksum(sfvChecksum) +
                                " SLAVE: " + Checksum.formatChecksum(fileChecksum));
                    }
                }
                // Run any post processing extensions
                doRescanPostProcess(request, workingDir, quiet);
            }
            if (session.isAborted()) {
                logger.warn("Session was aborted while running rescan");
            }
        }
        return response;
    }

    private long getFileCheckSum(FileHandle file, boolean forceRescan) throws FileNotFoundException, NoAvailableSlaveException {
        return forceRescan ? file.getCheckSumFromSlave() : file.getCheckSum();
    }

    private String checkSingleFile(Session session, String sfvEntryName, FileHandle file, long sfvChecksum, long fileChecksum,
                                   boolean deleteZeroByte, boolean deleteBad, boolean quiet) {
        long fileSize;
        String status = "";
        try {
            fileSize = file.getSize();
        } catch (FileNotFoundException e) {
            session.printOutput(200, "SFV: " + Checksum.formatChecksum(sfvChecksum) + " SLAVE: " + sfvEntryName + " MISSING");
            return null;
        }
        if (fileChecksum == 0L) {
            if (fileSize == 0L) {
                status = "ZEROBYTE";
                if (deleteZeroByte) {
                    try {
                        file.deleteUnchecked();
                        status += " - deleted";
                    } catch (FileNotFoundException e) {
                        // File already gone, all is good
                        logger.debug("Tried to delete {}, but file was already gone", file.getName(), e);
                    }
                }
            } else {
                status = "FAILED - failed to checksum file";
            }
        } else if (sfvChecksum == fileChecksum) {
            if (!quiet) {
                status = "OK";
            }
        } else {
            status = "FAILED - checksum mismatch";
            if (deleteBad) {
                try {
                    file.deleteUnchecked();
                } catch (FileNotFoundException e) {
                    // File already gone, all is good
                    logger.debug("Tried to delete {}, but file was already gone", file.getName(), e);
                }
            }
        }
        return status;
    }

    private SFVInfo getSFVInfo(DirectoryHandle dirHandle) throws NoAvailableSlaveException, SlaveUnavailableException {
        SFVInfo info = null;
        ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dirHandle);
        try {
            info = sfvData.getSFVInfo();
        } catch (FileNotFoundException e2) {
            // Need to carry on anyway but skip sfv checking, this allows any extensions to run
        } catch (IOException e2) {
            /* Unable to read sfv in this dir, silently ignore so not to add
             * useless output in recursive mode
             */
        }
        return info;
    }

    private void doRescanPostProcess(CommandRequest request, DirectoryHandle workingDir, boolean quiet) {
        for (RescanPostProcessDirInterface rescanAddon : _rescanAddons) {
            CommandRequest workingDirReq = (CommandRequest) request.clone();
            workingDirReq.setCurrentDirectory(workingDir);
            rescanAddon.postProcessDir(workingDirReq, quiet);
        }
    }
}
