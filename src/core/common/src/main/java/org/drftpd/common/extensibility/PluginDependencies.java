package org.drftpd.common.extensibility;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.RUNTIME)
public @interface PluginDependencies {
    Class<? extends PluginInterface>[] refs();
}
