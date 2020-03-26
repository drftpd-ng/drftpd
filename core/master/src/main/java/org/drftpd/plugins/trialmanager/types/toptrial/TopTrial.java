/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.plugins.trialmanager.types.toptrial;

import java.util.*;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.commands.CommandRequest;
import org.drftpd.commands.CommandResponse;
import org.drftpd.plugins.trialmanager.TrialType;

/**
 * @author CyBeR
 * @version $Id: TopTrial.java 1925 2009-06-15 21:46:05Z CyBeR $
 */

public class TopTrial extends TrialType {
    private int _list;
    private int _keep;
    private long _min;

    private int _minPercent;

    public TopTrial(Properties p, int confnum, String type) {
        super(p, confnum, type);

        try {
            _minPercent = Integer.parseInt(p.getProperty(confnum + ".percent", "0").trim());
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid minimum percentange for " + confnum + ".percent - Skipping Config");
        }

        try {
            _list = Integer.parseInt(p.getProperty(confnum + ".list", "").trim());
        } catch (NumberFormatException e) {
            _list = 10;
        }

        try {
            _min = Long.parseLong(p.getProperty(confnum + ".min", "-1").trim()) * 1000 * 1000 * 1000;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid Min for " + confnum + ".min - Skipping Config");
        }

        try {
            _keep = Integer.parseInt(p.getProperty(confnum + ".keep", "").trim());
        } catch (NumberFormatException e) {
            _keep = 10;
        }

    }


    public int getList() {
        return _list;
    }

    public int getKeep() {
        return _keep;
    }

    public long getMin() {
        return _min;
    }

    public int getMinPercent() {
        return _minPercent;
    }

    private void handlePassed(User user) {
        String[] commands = this.getPass().split(" ");

        if (commands.length > 1) {
            if (commands[0].equalsIgnoreCase("chgrp")) {
                for (int i = 1; i < commands.length; i++) {
                    user.toggleGroup(commands[i]);
                    logger.info("{} Toggled Group ('{}')", user.getName(), commands[i]);
                }
                return;
            }
            if (commands[0].equalsIgnoreCase("setgrp")) {
                user.setGroup(commands[1]);
                logger.info("{} Primary Group Set To ('{}')", user.getName(), commands[1]);
                return;
            }
        }
        logger.info("No Mode found for {} - Passed - {}", getEventType(), getPass());
    }

    private void handleFailed(User user) {
        String[] commands = this.getFail().split(" ");

        if (commands.length > 0) {
            if (commands.length > 1) {
                if (commands[0].equalsIgnoreCase("chgrp")) {
                    for (int i = 1; i < commands.length; i++) {
                        user.toggleGroup(commands[i]);
                        logger.info("{} Toggled Group ('{}')", user.getName(), commands[i]);
                    }
                    return;
                }
                if (commands[0].equalsIgnoreCase("setgrp")) {
                    user.setGroup(commands[1]);
                    logger.info("{} Primary Group Set To ('{}')", user.getName(), commands[1]);
                    return;
                }
            } else {
                if (commands[0].equalsIgnoreCase("delete")) {
                    user.setDeleted(true);
                    logger.info("{} Deleted", user.getName());
                    return;
                }
                if (commands[0].equalsIgnoreCase("purge")) {
                    user.setDeleted(true);
                    logger.info("{} Purged", user.getName());
                    user.purge();
                    return;
                }
            }
        }
        logger.info("No Mode found for {} - Failed - {}", getEventType(), getFail());
    }

    @Override
    public void doTrial() {
        int passed = 0;
        long minPercentage = getTop() / 100 * getMinPercent();

        ArrayList<User> users = getUsers();
        for (User user : users) {
            if ((user.getUploadedBytesForPeriod(getPeriod()) > this.getMin()) && (user.getUploadedBytesForPeriod(getPeriod()) >= minPercentage) && (passed < getKeep())) {
                passed++;
                handlePassed(user);
            } else {
                handleFailed(user);
            }
        }

        GlobalContext.getEventService().publish(new TopTrialEvent(getName(), users, getPeriod(), getKeep(), getMin()));

    }

    private long getTop() {
        long top = 0;
        ArrayList<User> users = getUsers();
        for (User user : users) {
            if (user.getUploadedBytesForPeriod(getPeriod()) > top) {
                top = user.getUploadedBytesForPeriod(getPeriod());
            }
        }
        return top;
    }

    private CommandResponse doTopTrial(CommandRequest request, ResourceBundle bundle, CommandResponse response, boolean top) {
        User requestuser;
        try {
            requestuser = request.getUserObject();
        } catch (NoSuchUserException e1) {
            //No Such User Exists - return
            return response;
        } catch (UserFileException e1) {
            // User File Corrupt - return
            return response;
        }

        /*
         * Gets the List Size
         */
        int list = getList();
        if (request.hasArgument()) {
            try {
                list = Integer.parseInt(request.getArgument().trim());
                if ((list < 1) || (list > 100)) {
                    list = getList();
                }
            } catch (NumberFormatException e) {
                list = getList();
            }
        }

        Map<String, Object> env2 = new HashMap<>();
        env2.put("name", getName());
        env2.put("min", Bytes.formatBytes(getMin()));
        env2.put("period", getPeriodStr());
        env2.put("time", getRemainingTime());
        env2.put("keep", getKeep());
        env2.put("percent", getMinPercent());

        ArrayList<User> users = getUsers();
        env2.put("racers", users.size());

        if (top) {
            if (getMin() > 0) {
                response.addComment(request.getSession().jprintf(bundle,  "toptrial.top.header.min", env2, requestuser));
            } else {
                response.addComment(request.getSession().jprintf(bundle,  "toptrial.top.header", env2, requestuser));
            }
        } else {
            if (getMin() > 0) {
                response.addComment(request.getSession().jprintf(bundle,  "toptrial.cut.header.min", env2, requestuser));
            } else {
                response.addComment(request.getSession().jprintf(bundle,  "toptrial.cut.header", env2, requestuser));
            }
        }

        int i = 1;
        long minPercentage = getTop() / 100 * getMinPercent();
        for (User user : users) {
            if (i > list) {
                break;
            }
            Map<String, Object> env = new HashMap<>();
            long uploaded = user.getUploadedBytesForPeriod(getPeriod());
            env.put("upbytes", Bytes.formatBytes(uploaded));
            env.put("usernick", user.getName());
            env.put("usergroup", user.getGroup());

            env.put("rank", i);
            if (i < 10) {
                env.put("rank", "0" + i);
            }

            if ((i < getKeep()) && (uploaded >= getMin()) && (uploaded >= minPercentage)) {
                //Passing
                if (top) {
                    response.addComment(request.getSession().jprintf(bundle,  "toptrial.top.passed", env, requestuser));
                }
            } else {
                //Failing
                if (top) {
                    response.addComment(request.getSession().jprintf(bundle,  "toptrial.top.failed", env, requestuser));
                } else {
                    response.addComment(request.getSession().jprintf(bundle,  "toptrial.cut.failed", env, requestuser));
                }
            }
            i++;
        }
        return response;
    }

    @Override
    public void doTop(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
        doTopTrial(request, bundle, response, true);
    }


    @Override
    public void doCut(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
        doTopTrial(request, bundle, response, false);
    }


    @Override
    public void doPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
        doTopTrialPassed(request, bundle, response);
    }


    private CommandResponse doTopTrialPassed(CommandRequest request, ResourceBundle bundle, CommandResponse response) {
        User requestuser = null;
        User checkuser = null;
        try {
            requestuser = request.getUserObject();
            if (request.hasArgument()) {
                checkuser = request.getSession().getUserNull(request.getArgument());
                if (requestuser == null) {
                    throw new NoSuchUserException();
                }
            } else {
                checkuser = requestuser;
            }
        } catch (NoSuchUserException e1) {
            //No Such User Exists - return
            return response;
        } catch (UserFileException e1) {
            // User File Corrupt - return
            return response;
        }

        boolean found = false;
        int count = 0;
        ArrayList<User> users = getUsers();
        User prevuser = null;
        for (User user : users) {
            ++count;
            if (user == checkuser) {
                found = true;
                Map<String, Object> env = new HashMap<>();
                env.put("name", this.getName());
                env.put("usernick", user.getName());
                env.put("usergroup", user.getGroup());
                env.put("rank", count);
                long uploaded = user.getUploadedBytesForPeriod(getPeriod());
                env.put("up", Bytes.formatBytes(uploaded));
                env.put("time", getRemainingTime());

                Map<String, Object> env2 = new HashMap<>();
                if (prevuser != null) {
                    env2.put("userlead", prevuser.getName());
                    env2.put("diff", Bytes.formatBytes(prevuser.getUploadedBytesForPeriod(getPeriod()) - user.getUploadedBytesForPeriod(getPeriod())));
                }

                if (count == 1) {
                    env.put("place", request.getSession().jprintf(bundle,  "toptrial.place.winning", env2, requestuser));
                } else {
                    env.put("place", request.getSession().jprintf(bundle,  "toptrial.place.losing", env2, requestuser));
                }

                if ((count < getKeep()) && (uploaded >= getMin())) {
                    response.addComment(request.getSession().jprintf(bundle,  "toptrial.place.passed", env, requestuser));
                } else {
                    response.addComment(request.getSession().jprintf(bundle,  "toptrial.place.failed", env, requestuser));
                }

                break;
            }
            prevuser = user;
        }

        if (!found) {
            Map<String, Object> env = new HashMap<>();
            env.put("name", request.getArgument());
            response.addComment(request.getSession().jprintf(bundle,  "toptrial.place.notfound", env, requestuser));
        }

        return response;
    }

}
