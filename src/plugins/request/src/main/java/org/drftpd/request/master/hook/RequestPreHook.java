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
package org.drftpd.request.master.hook;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.extensibility.CommandHook;
import org.drftpd.common.extensibility.HookType;
import org.drftpd.master.commands.*;
import org.drftpd.master.usermanager.User;
import org.drftpd.request.master.RequestSettings;
import org.drftpd.request.master.metadata.RequestData;
import org.drftpd.request.master.metadata.RequestUserData;

import java.util.StringTokenizer;

/**
 * @author scitz0
 * @version $Id$
 */
public class RequestPreHook {
    private static final Logger logger = LogManager.getLogger(RequestPreHook.class);

    @CommandHook(commands = "doSITE_REQUEST", priority = 10, type = HookType.PRE)
    public CommandRequestInterface doWklyAllotmentPreCheck(CommandRequest request) {

        User user = request.getSession().getUserNull(request.getUser());
        logger.debug("[doSITE_REQUEST::doWklyAllotmentPreCheck][Pre-hook] Checking if applicable");

        if (user != null) {

            double weekReqs = user.getConfigHelper().get(RequestUserData.WEEKREQS, 0);

            if (RequestSettings.getSettings().getRequestWeekMax() != 0 && weekReqs >= RequestSettings.getSettings().getRequestWeekMax()) {
                if (RequestSettings.getSettings().getRequestWeekExempt().check(user)) {
                    logger.debug("[doSITE_REQUEST::doWklyAllotmentPreCheck][Pre-hook] User {} is exempt from weekly request allotment", user.getName());
                } else {
                    // User is not exempted and max number of request this week is made already
                    request.setAllowed(false);
                    request.setDeniedResponse(new CommandResponse(530, "Access denied - You have reached max(" +
                            RequestSettings.getSettings().getRequestWeekMax() + ") number of requests per week"));
                }
            }
        } else {
            request.setAllowed(false);
            request.setDeniedResponse(new CommandResponse(530, "Access denied - No Such User"));
        }
        return request;
    }

    @CommandHook(commands = "doSITE_RENUSER", type = HookType.PRE)
    public CommandRequestInterface doPreRequestRenuser(CommandRequest request) {

        logger.debug("[doSITE_RENUSER::doPreRequestRenuser][Pre-hook] Invoked");
        // If there are no arguments it will be handled by the actual function
        if (request.hasArgument()) {
            StringTokenizer st = new StringTokenizer(request.getArgument());

            // If the argument count is wrong this will be handled in the actual function
            if (st.countTokens() == 2) {
                logger.debug("Setting RENUSER_FROM and RENUSER_TO");
                request.getSession().setObject(RequestUserData.RENUSER_FROM, st.nextToken());
                request.getSession().setObject(RequestUserData.RENUSER_TO, st.nextToken());

            }
        }

        // return the request command
        return request;
    }
}
