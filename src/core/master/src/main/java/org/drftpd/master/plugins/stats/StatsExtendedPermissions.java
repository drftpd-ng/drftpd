package org.drftpd.master.plugins.stats;

import org.drftpd.master.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.master.permissions.ExtendedPermissions;
import org.drftpd.master.permissions.PermissionDefinition;

import java.util.Arrays;
import java.util.List;

public class StatsExtendedPermissions implements ExtendedPermissions {

    @Override
    public List<PermissionDefinition> permissions() {
        PermissionDefinition noStatsUP = new PermissionDefinition("nostatsup",
                DefaultConfigHandler.class, "handlePathPerm");
        PermissionDefinition noStatsDN = new PermissionDefinition("nostatsdn",
                DefaultConfigHandler.class, "handlePathPerm");
        PermissionDefinition creditCheck = new PermissionDefinition("creditcheck",
                StatsHandler.class, "handleCreditCheck");
        PermissionDefinition creditLoss = new PermissionDefinition("creditloss",
                StatsHandler.class, "handleCreditLoss");
        PermissionDefinition creditLimit = new PermissionDefinition("creditlimit",
                StatsHandler.class, "handleCreditLimit");
        return Arrays.asList(noStatsDN, noStatsUP, creditCheck, creditLoss, creditLimit);
    }
}
