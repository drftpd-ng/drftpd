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
package org.drftpd.commands;

import f00f.net.irc.martyr.commands.RawCommand;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpRequest;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;

import org.drftpd.plugins.SiteBot;

import org.drftpd.usermanager.NoSuchUserException;
import org.tanesha.replacer.ReplacerEnvironment;


/**
 * @author mog
 * @version $Id$
 */
public class SiteBotManagement implements CommandHandler, CommandHandlerFactory {
    private static final Logger logger = Logger.getLogger(SiteBotManagement.class);

    public SiteBotManagement() {
        super();
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException, ImproperUsageException {

        SiteBot sitebot;

        try {
            sitebot = (SiteBot) conn.getGlobalContext().getConnectionManager().getGlobalContext().getFtpListener(SiteBot.class);
        } catch (ObjectNotFoundException e) {
            return new Reply(500, "SiteBot not loaded");
        }
        
        if (conn.getRequest().getCommand().startsWith("SITE BLOWFISH")) {
        	return doSITE_BLOWFISH(conn, sitebot);
        }

        if (!conn.getRequest().hasArgument()) {
            throw new ImproperUsageException();
        }

        FtpRequest req2 = new FtpRequest(conn.getRequest().getArgument());

        try {
            if (!conn.getUser().isAdmin()) {
                return Reply.RESPONSE_530_ACCESS_DENIED;
            }
        } catch (NoSuchUserException e1) {
            throw new RuntimeException(e1);
        }

        if (req2.getCommand().equals("RECONNECT")) {
            sitebot.reconnect();

            return new Reply(200,
                "Told bot to disconnect, auto-reconnect should handle the rest");
        } else if (req2.getCommand().equals("DISCONNECT")) {
            sitebot.disconnect();

            return new Reply(200, "Told bot to disconnect");
        } else if (req2.getCommand().equals("CONNECT")) {
            try {
                sitebot.connect();

                return new Reply(200, "Sitebot connected");
            } catch (Exception e) {
                logger.warn("", e);

                return new Reply(500, e.getMessage());
            }
        } else if (req2.getCommand().equals("SAY")) {
            sitebot.sayGlobal(req2.getArgument());

            return new Reply(200, "Said: " + req2.getArgument());
        } else if (req2.getCommand().equals("RAW")) {
            sitebot.getIRCConnection().sendCommand(new RawCommand(
                    req2.getArgument()));

            return new Reply(200, "Sent raw: " + req2.getArgument());
        }

        return new Reply(501,
            conn.jprintf(SiteBotManagement.class, "sitebot.usage"));
    }

    private Reply doSITE_BLOWFISH(BaseFtpConnection conn, SiteBot sitebot) {
    	if (!conn.isSecure()) {
            return new Reply(200,
                    conn.jprintf(SiteBotManagement.class, "blowfish.reject"));
    	}
    	try {
    		String _key = null;
    		if (conn.getRequest().hasArgument()) {
    			_key = sitebot.getBlowfishKey(conn.getRequest().getArgument(), conn.getUserNull());
    		} else {
    			_key = sitebot.getBlowfishKey(conn.getUserNull());
    		}
    		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
    		env.add("key", _key);
    		return new Reply(200, conn.jprintf(SiteBotManagement.class, "blowfish.accept", env));
    	} catch (ObjectNotFoundException e) {
    		return new Reply(200, conn.jprintf(SiteBotManagement.class, "blowfish.notenabled"));
    	}
    }

	public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
