package org.drftpd.common;

import org.drftpd.master.PluginInterface;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.RUNTIME)
public @interface PluginDependencies {
    Class<? extends PluginInterface>[] refs();
}
