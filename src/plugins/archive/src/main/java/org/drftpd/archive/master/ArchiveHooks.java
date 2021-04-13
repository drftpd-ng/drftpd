package org.drftpd.archive.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.archive.master.archivetypes.ArchiveType;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.extensibility.PluginInterface;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandRequestInterface;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.List;
import java.util.Properties;
import java.util.StringTokenizer;

public class ArchiveHooks {
    protected static final Logger logger = LogManager.getLogger(ArchiveHooks.class);

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

        Archive archive;
        try {
            archive = getArchive();
        } catch(ObjectNotFoundException e) {
            logger.debug("Archive plugin not loaded, nothing left to do", e);
            return request;
        }

        int count = 1;
        String type;
        Properties props = archive.getProperties();
        while ((type = PropertyHelper.getProperty(props, count + ".type", null)) != null) {
            type = type.trim();
            SectionInterface sec = archive.getGlobalContext().getSectionManager().getSection(PropertyHelper.getProperty(props, count + ".section", "").trim());
            ArchiveType archiveType = archive.getArchiveType(count, type, sec, props);
            if (archiveType != null) {
                for (int i = 1; ; i++) {
                    String slaveName = PropertyHelper.getProperty(props, count + ".slavename." + i, "");
                    if (slaveName.length() <= 0) {
                        // We are done
                        break;
                    }
                    if(slavename.trim().equalsIgnoreCase(slave.getName())) {
                        request.setAllowed(false);
                        request.setDeniedResponse(new CommandResponse(550, "Slave "+slave.getName()+" is still referenced in Archive configuration, not allowing delete"));
                        break;
                    }
                }
            }
            count++;
        }

        return request;
    }

    private Archive getArchive() throws ObjectNotFoundException {
        Archive archive;
        List<PluginInterface> pluginList = GlobalContext.getGlobalContext().getPlugins();
        for (PluginInterface pi : pluginList) {
            if (pi instanceof Archive) {
                archive = (Archive) pi;
                return archive;
            }
        }
        throw new ObjectNotFoundException("Archive is not loaded");
    }
}
