package org.drftpd.commands.pre;

import org.drftpd.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.common.ExtendedPermissions;
import org.drftpd.common.PermissionDefinition;

import java.util.Arrays;
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
