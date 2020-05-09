/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.master.commands;

import com.google.common.collect.Multimap;
import com.google.common.collect.MultimapBuilder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * @author djb61
 * @version $Id$
 */
public abstract class CommandInterface {

    private static final Logger logger = LogManager.getLogger(CommandInterface.class);

    protected String[] _featReplies;

    private Multimap<Integer, HookContainer> _postHooks;

    private Multimap<Integer, HookContainer> _preHooks;

    public CommandInterface() {
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    public synchronized void initialize(String method, String pluginName, StandardCommandManager cManager) {
        Multimap<Integer, HookContainer> postHooks = MultimapBuilder.treeKeys().linkedListValues().build();
        Multimap<Integer, HookContainer> preHooks = MultimapBuilder.treeKeys().linkedListValues().build();
        Set<Method> hooksMethods = GlobalContext.getHooksMethods();
        // TODO [DONE] @k2r Plug hooks
        logger.debug("[{}:{}] Looking for hooks to attach here", pluginName, method);
        try {
            for (Method annotatedMethod : hooksMethods) {
                Class<?> declaringClass = annotatedMethod.getDeclaringClass();
                CommandHook annotation = annotatedMethod.getAnnotation(CommandHook.class);
                int priority = annotation.priority();
                List<String> commands = Arrays.asList(annotation.commands());
                // boolean handleClass = commands.stream().filter(c -> method.matches(c)).collect(Collectors.toList()).size() > 0;

                boolean handleClass = commands.contains(method) || commands.contains("*");
                if (!handleClass) continue;

                Object hookClass = declaringClass.getConstructor().newInstance();
                HookType type = annotation.type();
                if (type.equals(HookType.PRE)) {
                    preHooks.put(priority, new HookContainer(annotatedMethod, hookClass));
                } else {
                    postHooks.put(priority, new HookContainer(annotatedMethod, hookClass));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for {} extension point 'PreHook', possibly the {} extension point definition has changed in the plugin.xml", pluginName, pluginName, e);
        }
        logger.debug("[{}:{}] Loaded [{}] prehooks and [{}] posthooks", pluginName, method, preHooks.size(), postHooks.size());
        _preHooks = preHooks;
        _postHooks = postHooks;
    }

    protected void doPostHooks(CommandRequestInterface request, CommandResponseInterface response) {
        for (HookContainer hook : _postHooks.values()) {
            Method m = hook.getMethod();
            try {
                m.invoke(hook.getHookInterfaceInstance(), request, response);
            } catch (Exception e) {
                // Not that important, this just means that this post hook failed and we'll just move onto the next one
                logger.error("Error while loading/invoking posthook {}", m.toString(), e.getCause());
            }
        }
    }

    protected CommandRequestInterface doPreHooks(CommandRequestInterface request) {
        request.setAllowed(true);
        for (HookContainer hook : _preHooks.values()) {
            Method m = hook.getMethod();
            try {
                request = (CommandRequestInterface) m.invoke(hook.getHookInterfaceInstance(), new Object[]{request});
            } catch (Exception e) {
                // Not that important, this just means that this pre hook failed and we'll just move onto the next one
                logger.error("Error while loading/invoking prehook {}", m.toString(), e.getCause());
            }
        }
        return request;
    }

    protected User getUserObject(String user) throws NoSuchUserException, UserFileException {
        return GlobalContext.getGlobalContext().getUserManager().getUserByName(user);
    }

    public String[] getFeatReplies() {
        return _featReplies;
    }

    protected boolean checkCustomPermission(CommandRequest request, String permissionName, String defaultPermission) {
        String permissionString = request.getProperties().getProperty(permissionName, defaultPermission);
        User user;
        try {
            user = request.getUserObject();
        } catch (NoSuchUserException | UserFileException e) {
            logger.warn("", e);
            return false;
        }
        return new Permission(permissionString).check(user);
    }

    /**
     * Called when the command instance has been unloaded from the parent command map. At this
     * point the command is no longer referenced or accessible, this method performs any cleanup
     * required at this point.
     */
    protected void unload() {
        AnnotationProcessor.unprocess(this);
    }
}
