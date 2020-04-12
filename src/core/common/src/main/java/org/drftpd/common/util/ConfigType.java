package org.drftpd.common.util;

public enum ConfigType {
    SLAVE("slave"),
    MASTER("master");

    public final String label;

    private ConfigType(String label) {
        this.label = label;
    }
}
