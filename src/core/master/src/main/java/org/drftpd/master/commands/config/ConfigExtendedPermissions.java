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
package org.drftpd.master.commands.config;

import org.drftpd.master.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.master.permissions.ExtendedPermissions;
import org.drftpd.master.permissions.PermissionDefinition;

import java.util.Arrays;
import java.util.List;

public class ConfigExtendedPermissions implements ExtendedPermissions {

    @Override
    public List<PermissionDefinition> permissions() {
        PermissionDefinition hideInWho = new PermissionDefinition("hideinwho",
                DefaultConfigHandler.class, "handlePathPerm");
        PermissionDefinition rejectSecure = new PermissionDefinition("userrejectsecure",
                DefaultConfigHandler.class, "handlePerm");
        PermissionDefinition rejectInsecure = new PermissionDefinition("userrejectinsecure",
                DefaultConfigHandler.class, "handlePerm");
        PermissionDefinition denyDirUncrypted = new PermissionDefinition("denydiruncrypted",
                DefaultConfigHandler.class, "handlePerm");
        PermissionDefinition denyDataUncrypted = new PermissionDefinition("denydatauncrypted",
                DefaultConfigHandler.class, "handlePerm");
        PermissionDefinition msgPath = new PermissionDefinition("msgpath",
                DefaultConfigHandler.class, "handleMsgPath");
        return Arrays.asList(hideInWho, rejectSecure, rejectInsecure, denyDataUncrypted, denyDirUncrypted, msgPath);
    }
}
