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

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.drftpd.sections.SectionInterface;

import org.tanesha.replacer.ReplacerEnvironment;

import java.util.Collection;
import java.util.Iterator;


/**
 * @author mog
 * @version $Id$
 */
public class Sections implements CommandHandler, CommandHandlerFactory {
    public Sections() {
        super();
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        Reply reply = new Reply(200);
        Collection sections = conn.getGlobalContext().getSectionManager()
                                  .getSections();
        ReplacerEnvironment env = new ReplacerEnvironment();

        for (Iterator iter = sections.iterator(); iter.hasNext();) {
            SectionInterface section = (SectionInterface) iter.next();
            env.add("section", section.getName());
            env.add("path", section.getPath());
            reply.addComment(conn.jprintf(Sections.class, "section", env));
        }

        return reply;
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
