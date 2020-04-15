package org.drftpd.common.extensibility;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface CommandHook {
    String[] commands();

    int priority() default 1;

    HookType type() default HookType.POST;
}
