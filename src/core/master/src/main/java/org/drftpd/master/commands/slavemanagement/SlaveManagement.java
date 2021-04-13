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
package org.drftpd.master.commands.slavemanagement;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.dynamicdata.KeyNotFoundException;
import org.drftpd.common.exceptions.DuplicateElementException;
import org.drftpd.common.exceptions.RemoteIOException;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.event.SlaveEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
import org.drftpd.master.network.Session;
import org.drftpd.master.slavemanagement.RemergeMessage;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SlaveManager;
import org.drftpd.master.slavemanagement.SlaveStatus;
import org.drftpd.master.slaveselection.filter.Filter;
import org.drftpd.master.slaveselection.filter.ScoreChart;
import org.drftpd.master.slaveselection.filter.SlaveSelectionManager;
import org.drftpd.master.vfs.CommitManager;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.slave.network.Transfer;

import java.io.IOException;
import java.util.*;
import java.util.Map.Entry;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class SlaveManagement extends CommandInterface {

    protected static final Logger logger = LogManager.getLogger(SlaveManagement.class);

    private ResourceBundle _bundle;

    public static void fillEnvWithSlaveStatus(Map<String, Object> env, SlaveStatus status) {
        env.put("disktotal", Bytes.formatBytes(status.getDiskSpaceCapacity()));
        env.put("diskfree", Bytes.formatBytes(status.getDiskSpaceAvailable()));
        env.put("diskused", Bytes.formatBytes(status.getDiskSpaceUsed()));
        try {
            env.put("slavesonline", "" + GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves().size());
        } catch (NoAvailableSlaveException e) {
            env.put("slavesonline", "0");
        }
        env.put("slavestotal", "" + GlobalContext.getGlobalContext().getSlaveManager().getSlaves().size());

        if (status.getDiskSpaceCapacity() == 0) {
            env.put("diskfreepercent", "n/a");
            env.put("diskusedpercent", "n/a");
        } else {
            env.put("diskfreepercent", ((status.getDiskSpaceAvailable() * 100) / status.getDiskSpaceCapacity()) + "%");
            env.put("diskusedpercent", ((status.getDiskSpaceUsed() * 100) / status.getDiskSpaceCapacity()) + "%");
        }

        env.put("xfers", "" + status.getTransfers());
        env.put("xfersdn", "" + status.getTransfersSending());
        env.put("xfersup", "" + status.getTransfersReceiving());
        env.put("xfersdown", "" + status.getTransfersSending());

        env.put("throughput", Bytes.formatBytes(status.getThroughput()) + "/s");
        env.put("throughputup", Bytes.formatBytes(status.getThroughputReceiving()) + "/s");
        env.put("throughputdown", Bytes.formatBytes(status.getThroughputSending()) + "/s");
    }

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        _bundle = cManager.getResourceBundle();

    }

    public CommandResponse doSLAVESELECT(CommandRequest request) throws ImproperUsageException {

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }
        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);
        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }
        String type = arguments.nextToken();
        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }
        String path = arguments.nextToken();
        if (!path.startsWith(VirtualFileSystem.separator)) {
            throw new ImproperUsageException();
        }
        char direction;
        if (type.equalsIgnoreCase("up")) {
            direction = Transfer.TRANSFER_RECEIVING_UPLOAD;
        } else if (type.equalsIgnoreCase("down")) {
            direction = Transfer.TRANSFER_SENDING_DOWNLOAD;
        } else {
            throw new ImproperUsageException();
        }
        Collection<RemoteSlave> slaves;
        try {
            slaves = GlobalContext.getGlobalContext().getSlaveManager().getAvailableSlaves();
        } catch (NoAvailableSlaveException e1) {
            return StandardCommandManager.genericResponse("RESPONSE_530_SLAVE_UNAVAILABLE");
        }
        SlaveSelectionManager ssm;
        try {
            ssm = (SlaveSelectionManager) GlobalContext.getGlobalContext().getSlaveSelectionManager();
        } catch (ClassCastException e) {
            return new CommandResponse(500,
                    "You are attempting to test filter.SlaveSelectionManager yet you're using def.SlaveSelectionManager");
        }
        CommandResponse response = new CommandResponse(500, "***End of SlaveSelection output***");
        Collection<Filter> filters = ssm.getFilterChain(type).getFilters();
        ScoreChart sc = new ScoreChart(slaves);
        for (Filter filter : filters) {
            try {
                filter.process(sc, null, null, direction, new FileHandle(path), null);
            } catch (NoAvailableSlaveException ignored) {}
        }
        for (ScoreChart.SlaveScore ss : sc.getSlaveScores()) {
            response.addComment(ss.getRSlave().getName() + "=" + ss.getScore());
        }
        return response;
    }

    public CommandResponse doKICKSLAVE(CommandRequest request) {
        Session session = request.getSession();

        if (!request.hasArgument()) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        RemoteSlave rslave;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
        } catch (ObjectNotFoundException e) {
            return new CommandResponse(200, "No such slave");
        }

        if (!rslave.isOnline()) {
            return new CommandResponse(200, "Slave is already offline");
        }

        rslave.setOffline("Slave kicked by " +
                session.getUserNull(request.getUser()).getName());

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    /**
     * Lists all slaves used by the master
     * USAGE: SITE SLAVES
     */
    public CommandResponse doSLAVES(CommandRequest request) {
        boolean showMore = request.hasArgument() &&
                (request.getArgument().equalsIgnoreCase("more"));

        Collection<RemoteSlave> slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
        CommandResponse response = new CommandResponse(200, "OK, " + slaves.size() + " slaves listed.");

        String slavestofind = GlobalContext.getConfig().getMainProperties().getProperty("default.slave.output");
        int slavesFound = 0;
        String slave = "all";
        if (request.hasArgument()) {
            slave = request.getArgument().toLowerCase();
        } else if (slavestofind != null) {
            slave = slavestofind;
        }

        for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
            String name = rslave.getName().toLowerCase();

            if ((!name.startsWith(slave)) && (!slave.equals("all"))) {
                continue;
            }

            response = addSlaveStatus(request, response, showMore, rslave);
            slavesFound = slavesFound + 1;
        }

        if (slavesFound == 0) {
            response.addComment(request.getSession().jprintf(_bundle, "slave.none", null, request.getUser()));
        }
        return response;
    }

    public CommandResponse doSlave(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        boolean showMore = false;
        StringTokenizer arguments = new StringTokenizer(request.getArgument());
        String slaveName = arguments.nextToken();
        RemoteSlave rslave;
        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
        } catch (ObjectNotFoundException e) {
            Map<String, Object> env = new HashMap<>();
            env.put("slavename", slaveName);
            response.addComment(request.getSession().jprintf(_bundle, "slave.notfound", env, request.getUser()));
            return response;
        }
        if (arguments.hasMoreTokens()) {
            String option = arguments.nextToken();
            if (option.equalsIgnoreCase("more")) {
                showMore = true;
            } else if (option.equalsIgnoreCase("queues")) {
                Map<String, Object> env = new HashMap<>();
                env.put("slavename", slaveName);
                env.put("renamesize", rslave.getRenameQueue().size());
                env.put("remergesize", rslave.getRemergeQueue().size());
                env.put("remergecrcsize", rslave.getCRCQueue().size());
                response.addComment(request.getSession().jprintf(_bundle,
                        "slave.queues", env, request.getUser()));
                return response;
            } else {
                throw new ImproperUsageException();
            }
        }
        if (arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }
        return addSlaveStatus(request, response, showMore, rslave);
    }

    private CommandResponse addSlaveStatus(CommandRequest request, CommandResponse response, boolean showMore, RemoteSlave rslave) {
        Session session = request.getSession();
        if (showMore) {
            response.addComment(rslave.moreInfo());
        }

        Map<String, Object> env = new HashMap<>();
        env.put("slavename", rslave.getName());
        try {
            env.put("slaveip", rslave.getPASVIP());
        } catch (SlaveUnavailableException e) {
            env.put("slaveip", "OFFLINE");
        }

        if (rslave.isOnline()) {
            if (!rslave.isAvailable()) {
                response.addComment(session.jprintf(_bundle, "slave.remerging", env, request.getUser()));
            } else {
                try {
                    SlaveStatus status = rslave.getSlaveStatus();
                    fillEnvWithSlaveStatus(env, status);
                    env.put("status", rslave.isRemerging() ? "REMERGING" : "ONLINE");
                    response.addComment(session.jprintf(_bundle, "slave.online", env, request.getUser()));
                } catch (SlaveUnavailableException e) {
                    // should never happen since we tested slave status w/ isOnline and isAvaiable.
                    throw new RuntimeException("There's a bug somewhere in the code, the slave was available now it isn't.", e);
                }
            }
        } else {
            response.addComment(session.jprintf(_bundle, "slave.offline", env, request.getUser()));
        }
        return response;
    }

    public CommandResponse doREMERGE(CommandRequest request) {
        if (!request.hasArgument()) {
            return StandardCommandManager.genericResponse("RESPONSE_501_SYNTAX_ERROR");
        }

        RemoteSlave rslave;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(request.getArgument());
        } catch (ObjectNotFoundException e) {
            return new CommandResponse(200, "No such slave");
        }

        if (!rslave.isAvailable()) {
            return new CommandResponse(200,
                    "Slave is still merging from initial connect");
        }

        if (rslave.isRemerging()) {
            return new CommandResponse(200,
                    "Slave is still remerging by a previous remerge command");
        }

        rslave.setRemerging(true);
        try {
            rslave.fetchResponse(SlaveManager.getBasicIssuer().issueRemergeToSlave(rslave,
                    request.getCurrentDirectory().getPath(), false, 0L, 0L, false), 0);
        } catch (RemoteIOException e) {
            rslave.setOffline("IOException during remerge()");

            return new CommandResponse(200, "IOException during remerge()");
        } catch (SlaveUnavailableException e) {
            rslave.setOffline("Slave Unavailable during remerge()");

            return new CommandResponse(200, "Slave Unavailable during remerge()");
        }

        // set remerge and crc threads to status finished so that threads may terminate cleanly
        rslave.setCRCThreadFinished();
        rslave.putRemergeQueue(new RemergeMessage(rslave));

        // Wait for remerge and crc queues to drain
        while (!rslave.getRemergeQueue().isEmpty() && !rslave.getCRCQueue().isEmpty()) {
            try {
                Thread.sleep(500);
            } catch (InterruptedException ignored) {}
        }

        if (rslave._remergePaused.get()) {
            String message = "Remerge was paused on slave after completion, issuing resume so not to break manual remerges";
            GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, rslave));
            try {
                SlaveManager.getBasicIssuer().issueRemergeResumeToSlave(rslave);
                rslave._remergePaused.set(false);
            } catch (SlaveUnavailableException e) {
                rslave.setOffline("Slave Unavailable during remerge()");
                return new CommandResponse(200, "Slave Unavailable during remerge()");
            }
        }

        String message = ("Remerge queueprocess finished");
        GlobalContext.getEventService().publishAsync(new SlaveEvent("MSGSLAVE", message, rslave));
        rslave.setRemerging(false);

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    /**
     * Usage: site slave slavename [set,addmask,delmask]
     *
     * @throws ImproperUsageException Thrown when the command is invoked with incorrect instructions
     */
    public CommandResponse doSLAVE(CommandRequest request) throws ImproperUsageException {
        Session session = request.getSession();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String slavename = arguments.nextToken();
        env.put("slavename", slavename);

        RemoteSlave rslave;

        try {
            rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(session.jprintf(_bundle,
                    "slave.notfound", env, request.getUser()));

            return response;
        }

        if (!arguments.hasMoreTokens()) {
            if (!rslave.getMasks().isEmpty()) {
                env.put("masks", rslave.getMasks());
                response.addComment(session.jprintf(_bundle,
                        "slave.masks", env, request.getUser()));
            }

            response.addComment(session.jprintf(_bundle,
                    "slave.data.header", request.getUser()));

            Map<Object, Object> props = rslave.getProperties();

            for (Entry<Object, Object> entry : props.entrySet()) {
                env.put("key", entry.getKey());
                env.put("value", entry.getValue());
                response.addComment(session.jprintf(_bundle,
                        "slave.data", env, request.getUser()));
            }

            return response;
        }

        String command = arguments.nextToken();

        if (command.equalsIgnoreCase("set")) {
            if (arguments.countTokens() != 2) {
                throw new ImproperUsageException();
            }

            String key = arguments.nextToken();
            String value = arguments.nextToken();
            rslave.setProperty(key, value);
            env.put("key", key);
            env.put("value", value);
            response.addComment(session.jprintf(_bundle,
                    "slave.set.success", env, request.getUser()));

            return response;
        } else if (command.equalsIgnoreCase("unset")) {
            if (arguments.countTokens() != 1) {
                throw new ImproperUsageException();
            }

            String key = arguments.nextToken();
            env.put("key", key);
            String value;
            try {
                value = rslave.removeProperty(key);
            } catch (KeyNotFoundException e) {
                response.addComment(session.jprintf(_bundle,
                        "slave.unset.failure", env, request.getUser()));
                return response;
            }
            env.put("value", value);
            response.addComment(session.jprintf(_bundle,
                    "slave.unset.success", env, request.getUser()));
            return response;
        } else if (command.equalsIgnoreCase("addmask")) {
            if (arguments.countTokens() != 1) {
                throw new ImproperUsageException();
            }

            String mask = arguments.nextToken();
            env.put("mask", mask);
            try {
                rslave.addMask(mask);
                response.addComment(session.jprintf(_bundle,
                        "slave.addmask.success", env, request.getUser()));
                return response;
            } catch (DuplicateElementException e) {
                response = new CommandResponse(501, session.jprintf(_bundle,
                        "slave.addmask.dupe", env, request.getUser()));
                return response;
            }
        } else if (command.equalsIgnoreCase("delmask")) {
            if (arguments.countTokens() != 1) {
                throw new ImproperUsageException();
            }

            String mask = arguments.nextToken();
            env.put("mask", mask);

            if (rslave.removeMask(mask)) {
                return new CommandResponse(200, session.jprintf(_bundle,
                        "slave.delmask.success", env, request.getUser()));
            }
            return new CommandResponse(501, session.jprintf(_bundle,
                    "slave.delmask.failed", env, request.getUser()));
        } else if (command.equalsIgnoreCase("shutdown")) {
            rslave.shutdown();
            return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        } else if (command.equalsIgnoreCase("queues")) {
            env.put("renamesize", rslave.getRenameQueue().size());
            env.put("remergesize", rslave.getRemergeQueue().size());
            env.put("remergecrcsize", rslave.getCRCQueue().size());
            response.addComment(session.jprintf(_bundle,
                    "slave.queues", env, request.getUser()));
            return response;
        }
        throw new ImproperUsageException();
    }

    public CommandResponse doDELSLAVE(CommandRequest request) throws ImproperUsageException {
        Session session = request.getSession();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String argument = request.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String slavename = arguments.nextToken();
        env.put("slavename", slavename);

        try {
            GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(session.jprintf(_bundle, "delslave.notfound", env, request.getUser()));

            return response;
        }

        GlobalContext.getGlobalContext().getSlaveManager().delSlave(slavename);
        response.addComment(session.jprintf(_bundle, "delslave.success", env, request.getUser()));

        // We try to reload the SlaveSelectionManager here to make sure it knows of the new slave
        try {
            GlobalContext.getGlobalContext().getSlaveSelectionManager().reload();
        } catch(IOException e) {
            logger.error("While removing a slave we reloaded the SlaveSelectionManager, however we trapped an exception");
            logger.error(e.getStackTrace());
            response.addComment("We received an Exception during reload of SlaveSelectionManager, this is unexpected and needs investigation");
        }

        return response;
    }

    public CommandResponse doADDSLAVE(CommandRequest request) throws ImproperUsageException {
        Session session = request.getSession();

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        StringTokenizer arguments = new StringTokenizer(request.getArgument());

        if (!arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
        }

        String slavename = arguments.nextToken();
        env.put("slavename", slavename);

        if (arguments.hasMoreTokens()) {
            throw new ImproperUsageException();
            // only one argument
        }

        try {
            GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
            return new CommandResponse(501, session.jprintf(_bundle, "addslave.exists", request.getUser()));
        } catch (ObjectNotFoundException ignored) {}

        GlobalContext.getGlobalContext().getSlaveManager().newSlave(slavename);
        // We try to reload the SlaveSelectionManager here to make sure it knows of the new slave
        try {
            GlobalContext.getGlobalContext().getSlaveSelectionManager().reload();
        } catch(IOException e) {
            logger.error("While adding a new slave we reloaded the SlaveSelectionManager, however we trapped an exception");
            logger.error(e.getStackTrace());
            response.addComment("We received an Exception during reload of SlaveSelectionManager, this is unexpected and needs investigation");
        }
        response.addComment(session.jprintf(_bundle, "addslave.success", env, request.getUser()));

        return response;
    }

    public CommandResponse doDiskfree(CommandRequest request) throws ImproperUsageException {
        if (request.hasArgument()) {
            throw new ImproperUsageException();
        }
        Map<String, Object> env = new HashMap<>();
        SlaveStatus status = GlobalContext.getGlobalContext().getSlaveManager().getAllStatus();
        fillEnvWithSlaveStatus(env, status);
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        response.addComment(request.getSession().jprintf(_bundle, "diskfree", env, request.getUser()));
        return response;
    }


    public CommandResponse doRemergequeue(CommandRequest request) throws ImproperUsageException {
        String slavesToFind = GlobalContext.getConfig().getMainProperties().getProperty("default.slave.output");
        String slave = "all";
        if (request.hasArgument()) {
            slave = request.getArgument().toLowerCase();
        } else if (slavesToFind != null) {
            slave = slavesToFind;
        }

        ArrayList<String> arr = new ArrayList<>();

        for (RemoteSlave rslave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
            String name = rslave.getName().toLowerCase();

            if ((!name.startsWith(slave)) && (!slave.equals("all"))) {
                continue;
            }

            int renameSize = rslave.getRenameQueue().size();
            int remergeSize = rslave.getRemergeQueue().size();
            int remergeCRCSize = rslave.getCRCQueue().size();
            if (!rslave.isOnline()) {
                arr.add(rslave.getName() + " is offline");
            } else if (!rslave.isRemerging()) {
                arr.add(rslave.getName() + " is online and not remerging");
            } else if (renameSize > 0 || remergeSize > 0 || remergeCRCSize > 0) {
                Map<String, Object> env = new HashMap<>();
                env.put("slavename", rslave.getName());
                env.put("renamesize", rslave.getRenameQueue().size());
                env.put("remergesize", rslave.getRemergeQueue().size());
                env.put("remergecrcsize", rslave.getCRCQueue().size());
                arr.add((request.getSession().jprintf(_bundle,
                        "slave.queues", env, request.getUser())));
            } else {
                arr.add(rslave.getName() + " remergequeue size is 0 but remerge is ongoing");
            }
        }
        arr.add("Total commit:" + CommitManager.getCommitManager().getQueueSize());

        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        for (String str : arr) {
            response.addComment(str);
        }

        return response;
    }

}

