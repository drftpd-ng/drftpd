package org.drftpd.common;

import org.drftpd.master.config.ConfigHandler;

public class PermissionDefinition {
    private String directive;
    private Class<? extends ConfigHandler> handler;
    private String method;

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
