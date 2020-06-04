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
package org.drftpd.statistics.master;

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
