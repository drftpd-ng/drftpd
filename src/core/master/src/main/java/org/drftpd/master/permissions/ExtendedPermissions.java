package org.drftpd.master.permissions;

import org.drftpd.master.permissions.PermissionDefinition;

import java.util.List;

public interface ExtendedPermissions {
    List<PermissionDefinition> permissions();
}
