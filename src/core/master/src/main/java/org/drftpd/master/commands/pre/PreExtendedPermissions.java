package org.drftpd.master.commands.pre;

import org.drftpd.master.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.master.permissions.ExtendedPermissions;
import org.drftpd.master.permissions.PermissionDefinition;

import java.util.Collections;
import java.util.List;

public class PreExtendedPermissions implements ExtendedPermissions {

    @Override
    public List<PermissionDefinition> permissions() {
        PermissionDefinition pre = new PermissionDefinition("pre",
                DefaultConfigHandler.class, "handlePathPerm");
        return Collections.singletonList(pre);
    }
}
