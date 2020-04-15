package org.drftpd.master.permissions;

import org.drftpd.master.config.ConfigHandler;

public class PermissionDefinition {
    private final String directive;
    private final Class<? extends ConfigHandler> handler;
    private final String method;

    public PermissionDefinition(String directive, Class<? extends ConfigHandler> handler, String method) {
        this.directive = directive;
        this.handler = handler;
        this.method = method;
    }

    public String getDirective() {
        return directive;
    }

    public Class<? extends ConfigHandler> getHandler() {
        return handler;
    }

    public String getMethod() {
        return method;
    }
}
