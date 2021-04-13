package org.drftpd.autofreespace.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.StringTokenizer;

public class AutoFreeSpaceHooks {

    protected static final Logger logger = LogManager.getLogger(AutoFreeSpaceHooks.class);

    @CommandHook(commands = "doDELSLAVE", priority = 100, type = HookType.PRE)
    public CommandRequestInterface doAutoFreeSpacePreHook(CommandRequest request) {
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
        String slavename = arguments.nextToken();
        RemoteSlave slave;
        try {
            slave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch(ObjectNotFoundException e) {
            // This is an error, but we'll let the command itself deal with it
            return request;
        }

        if (AutoFreeSpaceSettings.getSettings().getExcludeSlaves().contains(slave.getName())) {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(550, "Slave "+slave.getName()+" is still referenced in AutoFreeSpace configuration, not allowing delete"));
        }
        return request;
    }
}
