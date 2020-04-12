package org.drftpd.master.permissions.denydownload;

import org.drftpd.master.commands.config.hooks.DefaultConfigHandler;
import org.drftpd.master.permissions.ExtendedPermissions;
import org.drftpd.master.permissions.PermissionDefinition;

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
