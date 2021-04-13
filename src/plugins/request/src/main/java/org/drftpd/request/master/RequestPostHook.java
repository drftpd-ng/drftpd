/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.request.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.CommandResponse;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.usermanager.User;
import org.drftpd.request.master.metadata.RequestUserData;

import java.util.Properties;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestPostHook {

    private static final Logger logger = LogManager.getLogger(RequestPostHook.class);

    private boolean _decreaseWeekReqs;

    public void RequestPostHook() {
        readConfig();
        // Subscribe to events
        AnnotationProcessor.process(this);
    }

    @CommandHook(commands = "doREQUEST", priority = 10, type = HookType.POST)
    public void doREQUESTIncrement(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 257 && response.getCode() != 200) {
            // Request failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        user.getKeyedMap().incrementInt(RequestUserData.REQUESTS);
        user.getKeyedMap().incrementInt(RequestUserData.WEEKREQS);
        user.commit();
    }

    @CommandHook(commands = "doREQFILLED", priority = 10, type = HookType.POST)
    public void doREQFILLEDIncrement(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Reqfilled failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        user.getKeyedMap().incrementInt(RequestUserData.REQUESTSFILLED);
        user.commit();
    }

    @CommandHook(commands = "doREQDELETE", priority = 10, type = HookType.POST)
    public void doWklyAllotmentDecrease(CommandRequest request, CommandResponse response) {
        if (response.getCode() != 200) {
            // Reqdel failed, abort
            return;
        }
        User user = request.getSession().getUserNull(request.getUser());
        if (_decreaseWeekReqs && user.getKeyedMap().getObjectInteger(RequestUserData.WEEKREQS) > 0) {
            user.getKeyedMap().incrementInt(RequestUserData.WEEKREQS, -1);
            user.commit();
        }
    }

    /**
     * Reads 'config/plugins/request.conf'
     */
    private void readConfig() {
        Properties props = ConfigLoader.loadPluginConfig("request.conf");
        _decreaseWeekReqs = Boolean.parseBoolean(props.getProperty("request.weekdecrease", "false"));
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        readConfig();
    }
}
