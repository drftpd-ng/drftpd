package org.drftpd.permissions.denydownload;

import org.drftpd.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.common.ExtendedPermissions;
import org.drftpd.common.PermissionDefinition;

import java.util.Collections;
import java.util.List;

public class DenyDownloadPermissions implements ExtendedPermissions {

    @Override
    public List<PermissionDefinition> permissions() {
        PermissionDefinition denyDownload = new PermissionDefinition("denydownload",
                DefaultConfigHandler.class, "handlePathPerm");
        return Collections.singletonList(denyDownload);
    }
}
