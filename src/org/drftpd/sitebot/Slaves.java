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
package org.drftpd.sitebot;

import java.util.ArrayList;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.SiteBot;
import org.drftpd.slave.SlaveStatus;
import org.tanesha.replacer.ReplacerEnvironment;

import f00f.net.irc.martyr.commands.MessageCommand;


/**
 * @author mog
 * @version $Id$
 */
public class Slaves extends IRCCommand {
    private static final Logger logger = Logger.getLogger(Slaves.class);

    public Slaves() {
        super();
    }

    public ArrayList<String> doSlave(String args, MessageCommand msgc) {
        ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        String slaveName = args;
        try {
            RemoteSlave rslave = getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
            out.add(makeStatusString(rslave));
        } catch (ObjectNotFoundException e) {
            env.add("slave", slaveName);
            out.add(ReplacerUtils.jprintf("slaves.notfound", env, Slaves.class));
        }

        return out;
    }
    
    public ArrayList<String> doSlaves(String args, MessageCommand msgc) {
        ArrayList<String> out = new ArrayList<String>();
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
    	for(RemoteSlave rslave : getGlobalContext().getSlaveManager().getSlaves()) {
            out.add(makeStatusString(rslave));
        }
    	if (out.isEmpty()) {
            out.add(ReplacerUtils.jprintf("slaves.notfound", env, Slaves.class));
    	}
        return out;
    }
    
    private String makeStatusString(RemoteSlave rslave) {
        ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
        env.add("slave", rslave.getName());

        try {
            SlaveStatus status;

            try {
                status = rslave.getSlaveStatusAvailable();
            } catch (SlaveUnavailableException e1) {
                return ReplacerUtils.jprintf("slaves.offline", env, Slaves.class);
            }

            SiteBot.fillEnvSlaveStatus(env, status, getGlobalContext().getSlaveManager());

            return ReplacerUtils.jprintf("slaves", env, Slaves.class);
        } catch (RuntimeException t) {
            logger.log(Level.WARN, "Caught RuntimeException in !slaves loop", t);

            return ReplacerUtils.jprintf("slaves.offline", env, Slaves.class);
        }
    }
}
