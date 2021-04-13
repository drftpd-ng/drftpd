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
package org.drftpd.mirror.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.jobs.master.Job;
import org.drftpd.jobs.master.JobManager;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.commands.pre.Pre;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.io.FileNotFoundException;
import java.util.*;

/**
 * @author lh
 */
public class MirrorHooks {
    private static final Logger logger = LogManager.getLogger(MirrorHooks.class);

    public MirrorHooks() {
        reload();
        // Subscribe to events
        AnnotationProcessor.process(this);
        logger.info("MirrorPostHook successfully initialized");
    }

    private void reload() {
        MirrorSettings.getSettings().reload();
    }

    @CommandHook(commands = "doDELSLAVE", priority = 100, type = HookType.PRE)
    public CommandRequestInterface doMirrorPreHook(CommandRequest request) {
        // First handle syntax errors cases which will be handled in the normal command execution
        if (!request.hasArgument()) {
            // Syntax error but we'll let the command itself deal with it
            return request;
        }
        StringTokenizer arguments = new StringTokenizer(request.getArgument());
        if (!arguments.hasMoreTokens()) {
            // Syntax error but we'll let the command itself deal with it
            return request;
        }
        String slaveName = arguments.nextToken();
        RemoteSlave slave;
        try {
            slave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
        } catch(ObjectNotFoundException e) {
            // This is an error, but we'll let the command itself deal with it
            return request;
        }

        for (MirrorSettings.MirrorConfiguration mirrorConfig : MirrorSettings.getSettings().getConfigurations()) {
            if (mirrorConfig.getSlaves().contains(slave.getName()) || mirrorConfig.getExcludedSlaves().contains(slave.getName())){
                request.setAllowed(false);
                request.setDeniedResponse(new CommandResponse(550, "Slave "+slave.getName()+" is still referenced in Mirror configuration, not allowing delete"));
                break;
            }
        }
        return request;
    }

    @CommandHook(commands = "doPRE", type = HookType.PRE)
    public CommandRequestInterface doPREPreHook(CommandRequest request) {
        // The actual command handles all error cases, this is just to make sure the PREDIR Object is set before we run any other pre hooks
        if (request.hasArgument()) { // Handled correctly in doPRE
            StringTokenizer st = new StringTokenizer(request.getArgument());
            if (st.countTokens() == 2) { // Handled correctly in doPRE
                String sectionName = st.nextToken();
                String releaseName = st.nextToken();
                SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().getSection(sectionName);
                if (!section.getName().equals("")) { // Handled correctly in doPRE
                    User user = request.getSession().getUserNull(request.getUser());
                    String path = VirtualFileSystem.fixPath(releaseName);
                    if (!(path.startsWith(VirtualFileSystem.separator))) {
                        // Not a full path, let's make it one
                        if (request.getCurrentDirectory().isRoot()) {
                            path = VirtualFileSystem.separator + path;
                        } else {
                            path = request.getCurrentDirectory().getPath() + VirtualFileSystem.separator + path;
                        }
                    }
                    int stoppedJobs = 0;
                    int activeJobs = 0;
                    for (Job job : getJobManager().getAllJobsFromQueue()) {
                        // Make sure length wise we are correct here
                        if (job.getFile().getPath().length() >= path.length()) {
                            if (job.getFile().getPath().substring(0, path.length()).equalsIgnoreCase(path)) {
                                if (job.isTransferring()) {
                                    logger.warn("Cannot stop Job [{}] as it is already transferring", job.toString());
                                    activeJobs++;
                                } else {
                                    logger.warn("Stopping Job [{}] as {} requested {} to be pre'd", job.toString(), user.getName(), releaseName);
                                    getJobManager().stopJob(job);
                                    stoppedJobs++;
                                }
                            }
                        }
                    }
                    logger.debug("We found {} active jobs (could not be stopped) and stopped {} jobs because of {} being pre'd", activeJobs, stoppedJobs, releaseName);
                }
            }
        }

        return request;
    }

    @CommandHook(commands = "doPRE", type = HookType.POST)
    public void doPREPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 250) {
            // PRE failed, abort
            return;
        }

        if (MirrorSettings.getSettings().getUnmirrorTime() == 0L) {
            // Nothing to do
            return;
        }

        try {
            PRETask preTask = new PRETask(response.getObject(Pre.PREDIR));
            GlobalContext.getGlobalContext().getTimer().schedule(preTask, MirrorSettings.getSettings().getUnmirrorTime());
        } catch (KeyNotFoundException e) {
            logger.warn("Unable to activate UnMirror timer as PREDIR variable has not been set (bug?/error?)");
        } catch (IllegalStateException e) {
            logger.error("Unable to start UnMirror timer task on GlobalContext Timer", e);
        }
    }

    @CommandHook(commands = "doSTOR", priority = 100, type = HookType.POST)
    public void doSTORPostHook(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 226) {
            // STOR Failed, skip
            return;
        }
        FileHandle file;
        try {
            file = request.getCurrentDirectory().getFileUnchecked(request.getArgument());
        } catch (Exception e) {
            // File not available any more, transfer must have been aborted
            return;
        }

        // Check if there is a valid configuration for this path
        MirrorSettings.MirrorConfiguration activeSetting = null;
        for (MirrorSettings.MirrorConfiguration setting : MirrorSettings.getSettings().getConfigurations()) {
            for (String pattern : setting.getPaths()) {
                if (file.getPath().matches(pattern)) {
                    // Setting matched the path
                    activeSetting = setting;
                    // Found a match, break the loop early
                    break;
                }
            }
            if (activeSetting != null) {
                // Setting valid but check excluded paths also
                for (String pattern : setting.getExcludedPaths()) {
                    if (file.getPath().matches(pattern)) {
                        // Excluded for this setting based on pattern
                        activeSetting = null;
                        // Break the loop early
                        break;
                    }
                }
            }
            // Setting still valid?
            // If so no need to continue, only one mirror configuration can be valid
            if (activeSetting != null) {
                break;
            }
        }
        // If we find no setting at all to match there is nothing for us to do
        if (activeSetting == null) {
            return;
        }

        List<String> mirrorSlaves;
        try {
            mirrorSlaves = new ArrayList<>(file.getSlaveNames());
        } catch (FileNotFoundException e) {
            // file deleted, no problem, just exit
            return;
        }
        if (activeSetting.getSlaves().size() > 0) {
            mirrorSlaves.addAll(activeSetting.getSlaves());
        } else {
            try {
                for (RemoteSlave slave : GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves()) {
                    mirrorSlaves.add(slave.getName());
                }
            } catch (NoAvailableSlaveException e) {
                // No need to continue
                return;
            }
            if (activeSetting.getExcludedSlaves().size() > 0) {
                mirrorSlaves.removeAll(activeSetting.getExcludedSlaves());
            }
        }

        if (activeSetting.getNbrOfMirrors() <= mirrorSlaves.size()) {
            // We got enough slaves, proceed and add job to queue
            logger.debug("Adding {} to job queue with {} slaves {}", file.getPath(), mirrorSlaves.size(), mirrorSlaves.toString());
            getJobManager().addJobToQueue(new Job(file, mirrorSlaves, activeSetting.getPriority(), activeSetting.getNbrOfMirrors()));
        } else {
            logger.warn("Not adding {} to job queue, not enough slaves available.", file.getPath());
        }
    }

    /*
     * Gets the JobManager, hopefully its loaded.
     */
    public JobManager getJobManager() {
        for (PluginInterface plugin : GlobalContext.getGlobalContext().getPlugins()) {
            if (plugin instanceof JobManager) {
                return (JobManager) plugin;
            }
        }
        throw new RuntimeException("JobManager is not loaded");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        reload();
    }

    private static class PRETask extends TimerTask {
        private final DirectoryHandle _dir;

        public PRETask(DirectoryHandle dir) {
            _dir = dir;
        }

        public void run() {
            try {
                MirrorUtils.unMirrorDir(_dir, null, MirrorSettings.getSettings().getUnmirrorExcludePaths());
            } catch (FileNotFoundException e) {
                logger.error("Unmirror error:", e);
            }
        }
    }
}