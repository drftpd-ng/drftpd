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
package net.sf.drftpd.master.command.plugins;

import com.Ostermiller.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.slave.SlaveStatus;

import org.drftpd.commands.CommandHandler;
import org.drftpd.commands.CommandHandlerFactory;
import org.drftpd.commands.UnhandledCommandException;

import org.drftpd.plugins.SiteBot;

import org.tanesha.replacer.ReplacerEnvironment;

import java.io.IOException;

import java.util.Collection;
import java.util.Iterator;
import java.util.Map;


/**
 * @author mog
 * @author zubov
 * @version $Id: SlaveManagement.java,v 1.4 2004/10/05 02:11:22 mog Exp $
 */
public class SlaveManagement implements CommandHandlerFactory, CommandHandler {
    public void unload() {
    }

    public void load(CommandManagerFactory initializer) {
    }

    private FtpReply doSITE_CHECKSLAVES(BaseFtpConnection conn) {
        return new FtpReply(200,
            "Ok, " + conn.getSlaveManager().verifySlaves() +
            " stale slaves removed");
    }

    private FtpReply doSITE_KICKSLAVE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return FtpReply.RESPONSE_501_SYNTAX_ERROR;
        }

        RemoteSlave rslave;

        try {
            rslave = conn.getGlobalContext().getSlaveManager().getSlave(conn.getRequest()
                                                                            .getArgument());
        } catch (ObjectNotFoundException e) {
            return new FtpReply(200, "No such slave");
        }

        if (!rslave.isAvailable()) {
            return new FtpReply(200, "Slave is already offline");
        }

        rslave.setOffline("Slave kicked by " +
            conn.getUserNull().getUsername());

        return FtpReply.RESPONSE_200_COMMAND_OK;
    }

    /** Lists all slaves used by the master
     * USAGE: SITE SLAVES
     *
     */
    private FtpReply doSITE_SLAVES(BaseFtpConnection conn) {
        boolean showRMI = conn.getRequest().hasArgument() &&
            (conn.getRequest().getArgument().indexOf("rmi") != -1);

        if (showRMI && !conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        boolean showPlain = !conn.getRequest().hasArgument() ||
            (conn.getRequest().getArgument().indexOf("plain") != -1);
        Collection slaves = conn.getSlaveManager().getSlaves();
        FtpReply response = new FtpReply(200,
                "OK, " + slaves.size() + " slaves listed.");

        for (Iterator iter = conn.getSlaveManager().getSlaves().iterator();
                iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (showRMI) {
                response.addComment(rslave.toString());
            }

            if (showPlain) {
                ReplacerEnvironment env = new ReplacerEnvironment();
                env.add("slave", rslave.getName());

                try {
                    SlaveStatus status = rslave.getStatusAvailable();
                    SiteBot.fillEnvSlaveStatus(env, status,
                        conn.getSlaveManager());
                    response.addComment(conn.jprintf(SlaveManagement.class,
                            "slaves", env));
                } catch (SlaveUnavailableException e) {
                    response.addComment(conn.jprintf(SlaveManagement.class,
                            "slaves.offline", env));
                }
            }
        }

        return response;
    }

    private FtpReply doSITE_REMERGE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        if (!conn.getRequest().hasArgument()) {
            return FtpReply.RESPONSE_501_SYNTAX_ERROR;
        }

        RemoteSlave rslave;

        try {
            rslave = conn.getGlobalContext().getSlaveManager().getSlave(conn.getRequest()
                                                                            .getArgument());
        } catch (ObjectNotFoundException e) {
            return new FtpReply(200, "No such slave");
        }

        if (!rslave.isAvailable()) {
            return new FtpReply(200, "Slave is offline");
        }

        try {
            conn.getGlobalContext().getSlaveManager().remerge(rslave);
        } catch (IOException e) {
            rslave.setOffline("IOException during remerge()");

            return new FtpReply(200, "IOException during remerge()");
        } catch (SlaveUnavailableException e) {
            rslave.setOffline("Slave Unavailable during remerge()");

            return new FtpReply(200, "Slave Unavailable during remerge()");
        }

        return FtpReply.RESPONSE_200_COMMAND_OK;
    }

    /**
     * Usage: site slave slavename [set,addmask,delmask]
     */
    private FtpReply doSITE_SLAVE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        FtpReply response = new FtpReply(200);
        ReplacerEnvironment env = new ReplacerEnvironment();
        FtpRequest ftpRequest = conn.getRequest();

        if (!ftpRequest.hasArgument()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "slave.usage"));
        }

        String argument = ftpRequest.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "slave.usage"));
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);

        RemoteSlave rslave = null;

        try {
            rslave = conn.getGlobalContext().getSlaveManager().getSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(conn.jprintf(SlaveManagement.class,
                    "slave.notfound", env));

            return response;
        }

        if (!arguments.hasMoreTokens()) {
            if (rslave.getMasks().size() > 0) {
                String masks = new String();

                for (Iterator iter = rslave.getMasks().iterator();
                        iter.hasNext();) {
                    masks = masks + ((String) iter.next()) + ",";
                }

                masks = masks.substring(0, masks.length() - 1);
                env.add("masks", masks);
                response.addComment(conn.jprintf(SlaveManagement.class,
                        "slave.masks", env));
            }

            response.addComment(conn.jprintf(SlaveManagement.class,
                    "slave.data.header", env));

            Map props = rslave.getProperties();

            for (Iterator iter = props.keySet().iterator(); iter.hasNext();) {
                Object key = iter.next();
                Object value = props.get(key);
                env.add("key", key);
                env.add("value", value);
                response.addComment(conn.jprintf(SlaveManagement.class,
                        "slave.data", env));
            }

            return response;
        }

        String command = arguments.nextToken();

        if (command.equalsIgnoreCase("set")) {
            if (arguments.countTokens() != 2) {
                return new FtpReply(501,
                    conn.jprintf(SlaveManagement.class, "slave.set.usage"));
            }

            String key = arguments.nextToken();
            String value = arguments.nextToken();
            rslave.setProperty(key, value);
            env.add("key", key);
            env.add("value", value);
            response.addComment(conn.jprintf(SlaveManagement.class,
                    "slave.set.success", env));

            return response;
        } else if (command.equalsIgnoreCase("addmask")) {
            if (arguments.countTokens() != 1) {
                return new FtpReply(501,
                    conn.jprintf(SlaveManagement.class, "slave.addmask.usage"));
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);

            if (rslave.addMask(mask)) {
                response.addComment(conn.jprintf(SlaveManagement.class,
                        "slave.addmask.success", env));

                return response;
            }

            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "slave.addmask.failed"));
        } else if (command.equalsIgnoreCase("delmask")) {
            if (arguments.countTokens() != 1) {
                return new FtpReply(501,
                    conn.jprintf(SlaveManagement.class, "slave.delmask.usage"));
            }

            String mask = arguments.nextToken();
            env.add("mask", mask);

            if (rslave.removeMask(mask)) {
                response.addComment(conn.jprintf(SlaveManagement.class,
                        "slave.delmask.success", env));
            } else {
                response.addComment(conn.jprintf(SlaveManagement.class,
                        "slave.delmask.failed", env));
            }
        }

        return new FtpReply(501,
            conn.jprintf(SlaveManagement.class, "slave.usage"));
    }

    public FtpReply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE CHECKSLAVES".equals(cmd)) {
            return doSITE_CHECKSLAVES(conn);
        }

        if ("SITE KICKSLAVE".equals(cmd)) {
            return doSITE_KICKSLAVE(conn);
        }

        if ("SITE SLAVES".equals(cmd)) {
            return doSITE_SLAVES(conn);
        }

        if ("SITE REMERGE".equals(cmd)) {
            return doSITE_REMERGE(conn);
        }

        if ("SITE SLAVE".equals(cmd)) {
            return doSITE_SLAVE(conn);
        }

        if ("SITE ADDSLAVE".equals(cmd)) {
            return doSITE_ADDSLAVE(conn);
        }

        if ("SITE DELSLAVE".equals(cmd)) {
            return doSITE_DELSLAVE(conn);
        }

        throw UnhandledCommandException.create(SlaveManagement.class,
            conn.getRequest());
    }

    private FtpReply doSITE_DELSLAVE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        FtpReply response = new FtpReply(200);
        ReplacerEnvironment env = new ReplacerEnvironment();
        FtpRequest ftpRequest = conn.getRequest();

        if (!ftpRequest.hasArgument()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "delslave.usage"));
        }

        String argument = ftpRequest.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "delslave.usage"));
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);

        try {
            conn.getGlobalContext().getSlaveManager().getSlave(slavename);
        } catch (ObjectNotFoundException e) {
            response.addComment(conn.jprintf(SlaveManagement.class,
                    "delslave.notfound", env));

            return response;
        }

        conn.getGlobalContext().getSlaveManager().delSlave(slavename);
        response.addComment(conn.jprintf(SlaveManagement.class,
                "delslave.success", env));

        return response;
    }

    private FtpReply doSITE_ADDSLAVE(BaseFtpConnection conn) {
        if (!conn.getUserNull().isAdmin()) {
            return FtpReply.RESPONSE_530_ACCESS_DENIED;
        }

        FtpReply response = new FtpReply(200);
        ReplacerEnvironment env = new ReplacerEnvironment();
        FtpRequest ftpRequest = conn.getRequest();

        if (!ftpRequest.hasArgument()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "addslave.usage"));
        }

        String argument = ftpRequest.getArgument();
        StringTokenizer arguments = new StringTokenizer(argument);

        if (!arguments.hasMoreTokens()) {
            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "addslave.usage"));
        }

        String slavename = arguments.nextToken();
        env.add("slavename", slavename);

        try {
            conn.getGlobalContext().getSlaveManager().getSlave(slavename);

            return new FtpReply(501,
                conn.jprintf(SlaveManagement.class, "addslave.exists"));
        } catch (ObjectNotFoundException e) {
        }

        conn.getSlaveManager().addSlave(new RemoteSlave(slavename,
                conn.getSlaveManager()));
        response.addComment(conn.jprintf(SlaveManagement.class,
                "addslave.success", env));

        return response;
    }

    /* (non-Javadoc)
     * @see net.sf.drftpd.master.command.CommandHandler#initialize(net.sf.drftpd.master.BaseFtpConnection)
     */
    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String[] getFeatReplies() {
        return null;
    }
}
