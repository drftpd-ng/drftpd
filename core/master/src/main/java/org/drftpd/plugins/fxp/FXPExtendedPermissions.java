package org.drftpd.plugins.fxp;

import org.drftpd.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.common.ExtendedPermissions;
import org.drftpd.common.PermissionDefinition;

import java.util.Arrays;
import java.util.List;

public class FXPExtendedPermissions implements ExtendedPermissions {

    @Override
    public List<PermissionDefinition> permissions() {
        PermissionDefinition denyDn = new PermissionDefinition("deny_dnfxp",
                DefaultConfigHandler.class, "handlePathPerm");
        PermissionDefinition denyUp = new PermissionDefinition("deny_upfxp",
                DefaultConfigHandler.class, "handlePathPerm");
        return Arrays.asList(denyDn, denyUp);
    }
}
