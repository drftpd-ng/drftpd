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
package org.drftpd.master.sitebot;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.master.network.Session;
import org.drftpd.master.usermanager.User;

import java.util.ArrayList;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author djb61
 * @version $Id$
 */
@SuppressWarnings("serial")
public class ServiceCommand extends Session {

    public static final Key<String> IDENT = new Key<>(ServiceCommand.class, "ident");

    public static final Key<UserDetails> IRCUSER = new Key<>(ServiceCommand.class, "ircuser");

    public static final Key<String> SOURCE = new Key<>(ServiceCommand.class, "source");

    private final transient SiteBot _bot;

    private final ArrayList<OutputWriter> _outputs;

    private final UserDetails _runningUser;

    public ServiceCommand(SiteBot bot, ArrayList<OutputWriter> outputs, UserDetails runningUser, String ident, String source) {
        _bot = bot;
        _outputs = outputs;
        setObject(IRCUSER, runningUser);
        setObject(IDENT, ident);
        if (source != null) {
            setObject(SOURCE, source);
        }
        _runningUser = runningUser;
    }

    public void printOutput(Object o) {
        sendOutput(o);
    }

    public void printOutput(int code, Object o) {
        sendOutput(o);
    }

    private void sendOutput(Object o) {
        StringTokenizer st = new StringTokenizer(o.toString(), "\n");
        while (st.hasMoreTokens()) {
            String token = st.nextToken();
            for (OutputWriter output : _outputs) {
                output.sendMessage(token);
            }
        }
    }

    public String getIdent() {
        return getObject(IDENT, null);
    }

    public UserDetails getIrcUser() {
        return getObject(IRCUSER, null);
    }

    public String getSource() {
        return getObject(SOURCE, _runningUser.getNick());
    }

    public String[] getDestination() {
        String[] outputs = new String[_outputs.size()];
        for (int i = 0; i < outputs.length; i++) {
            outputs[i] = _outputs.get(i).getDestination();
        }
        return outputs;
    }

    public SiteBot getBot() {
        return _bot;
    }

    public boolean isSecure() {
        return _bot.getConfig().getBlowfishEnabled();
    }

    @Override
    public Map<String, Object> getReplacerEnvironment(Map<String, Object> inheritedEnv, User user) {
        Map<String, Object> env = super.getReplacerEnvironment(inheritedEnv, user);
        env.putAll(SiteBot.GLOBAL_ENV);
        return env;
    }

    @Override
    public void abortCommand() {
        synchronized (_runningUser) {
            for (ServiceCommand runningCommand : _runningUser.getCommandSessions()) {
                runningCommand.setAborted();
            }
        }
    }

    protected void setAborted() {
        super.abortCommand();
    }
}
