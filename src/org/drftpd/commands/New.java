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
import net.sf.drftpd.master.command.plugins.Textoutput;

import org.apache.log4j.Logger;

import org.drftpd.Bytes;
import org.drftpd.Time;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.sections.SectionInterface;

import org.tanesha.replacer.ReplacerEnvironment;

import java.io.IOException;

import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;


/**
 * @version $Id$
 * @author zubov
 */
public class New implements CommandHandlerFactory, CommandHandler {
    private static final Logger logger = Logger.getLogger(New.class);

    public New() {
        super();
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException {
        Reply reply = new Reply(200);
        Collection sections = conn.getGlobalContext().getConnectionManager()
                                  .getGlobalContext().getSectionManager()
                                  .getSections();
        int count = 20;

        try {
            count = Integer.parseInt(conn.getRequest().getArgument());
        } catch (Exception e) {
            // Ignore and output the 20 freshest...  
        }

        // Collect all new files (= directories hopefully!) from all sections,
        // and sort them.
        Collection<LinkedRemoteFileInterface> directories = new TreeSet<LinkedRemoteFileInterface>(
				new DateComparator());

        for (Iterator iter = sections.iterator(); iter.hasNext();) {
            SectionInterface section = (SectionInterface) iter.next();
            directories.addAll(section.getFile().getDirectories());
        }

        try {
            Textoutput.addTextToResponse(reply, "new_header");
        } catch (IOException ioe) {
            logger.warn("Error reading new_header", ioe);
        }

        // Print the reply! 
        ReplacerEnvironment env = new ReplacerEnvironment();
        int pos = 1;

        for (Iterator iter = directories.iterator();
                iter.hasNext() && (pos <= count); pos++) {
            LinkedRemoteFileInterface dir = (LinkedRemoteFileInterface) iter.next();
            env.add("pos", "" + pos);
            env.add("name", dir.getName());
            env.add("diruser", dir.getUsername());
            env.add("files", "" + dir.dirSize());
            env.add("size", Bytes.formatBytes(dir.length()));
            env.add("age",
                "" +
                Time.formatTime(System.currentTimeMillis() -
                    dir.lastModified()));
            reply.addComment(conn.jprintf(New.class, "new", env));
        }

        try {
            Textoutput.addTextToResponse(reply, "new_footer");
        } catch (IOException ioe) {
            logger.warn("Error reading new_footer", ioe);
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

    private class DateComparator implements Comparator {
        public int compare(Object o1, Object o2) {
            if (!(o1 instanceof LinkedRemoteFileInterface &&
                    o2 instanceof LinkedRemoteFileInterface)) {
                throw new ClassCastException("Not a LinkedRemoteFile");
            }

            LinkedRemoteFileInterface f1 = (LinkedRemoteFileInterface) o1;
            LinkedRemoteFileInterface f2 = (LinkedRemoteFileInterface) o2;

            if (f1.lastModified() == f2.lastModified()) {
                return 0;
            }

            return (f1.lastModified() > f2.lastModified()) ? (-1) : 1;
        }

        public boolean equals(Object o) {
            if (!(o instanceof DateComparator)) {
                return false;
            }

            return super.equals(o);
        }
    }
}
