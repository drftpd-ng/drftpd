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

import java.io.FileNotFoundException;
import java.lang.reflect.Constructor;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;

import org.apache.log4j.Logger;
import org.drftpd.master.RemoteSlave;
import org.drftpd.mirroring.ArchiveHandler;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.mirroring.DuplicateArchiveException;
import org.drftpd.plugins.Archive;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.ObjectNotValidException;
import org.tanesha.replacer.ReplacerEnvironment;


/*
 * @author zubov
 * @version $Id$
 */
public class ArchiveCommandHandler implements CommandHandler, CommandHandlerFactory {
    private static final Logger logger = Logger.getLogger(ArchiveCommandHandler.class);

    public ArchiveCommandHandler() {
        super();
    }

    public Reply execute(BaseFtpConnection conn)
        throws UnhandledCommandException, ImproperUsageException {
        String cmd = conn.getRequest().getCommand();

        if ("SITE LISTARCHIVETYPES".equals(cmd)) {
            return doLISTARCHIVETYPES(conn);
        }

        if ("SITE ARCHIVE".equals(cmd)) {
            return doARCHIVE(conn);
        }

        throw UnhandledCommandException.create(ArchiveCommandHandler.class,
            conn.getRequest());
    }

    private Reply doARCHIVE(BaseFtpConnection conn) throws ImproperUsageException {
        Reply reply = new Reply(200);
        ReplacerEnvironment env = new ReplacerEnvironment();

        if (!conn.getRequest().hasArgument()) {
        	throw new ImproperUsageException();
        }

        StringTokenizer st = new StringTokenizer(conn.getRequest().getArgument());
        String dirname = st.nextToken();
        DirectoryHandle dir;

        try {
			dir = conn.getCurrentDirectory().getDirectory(dirname);
		} catch (FileNotFoundException e1) {
			reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
					"help.archive", env));
			env.add("dirname", dirname);
			reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
					"archive.baddir", env));

			return reply;
		} catch (ObjectNotValidException e) {
			reply.addComment("Archive only works on Directories");
			return reply;
		}

        Archive archive;

        try {
            archive = (Archive) conn.getGlobalContext().getFtpListener(Archive.class);
        } catch (ObjectNotFoundException e3) {
            reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                    "archive.loadarchive", env));

            return reply;
        }

        String archiveTypeName = null;
        ArchiveType archiveType = null;
        SectionInterface section = conn.getGlobalContext().getSectionManager()
                                       .getSection(dir.getPath());

        if (st.hasMoreTokens()) { // load the specific type
            archiveTypeName = st.nextToken();

            Class[] classParams = {
                    org.drftpd.plugins.Archive.class,
                    SectionInterface.class, Properties.class
                };
            Constructor constructor = null;

            try {
                constructor = Class.forName(
                        "org.drftpd.mirroring.archivetypes." + archiveTypeName)
                                   .getConstructor(classParams);
            } catch (Exception e1) {
                logger.debug("Serious error, your ArchiveType for section " +
                    section.getName() +
                    " is incompatible with this version of DrFTPD", e1);
                reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                        "archive.incompatible", env));

                return reply;
            }

            Properties props = new Properties();

            while (st.hasMoreTokens()) {
                addConfig(props, st.nextToken(), section);
            }

            Object[] objectParams = { archive, section, props };

            try {
                archiveType = (ArchiveType) constructor.newInstance(objectParams);
            } catch (Exception e2) {
                logger.warn("Unable to load ArchiveType for section " +
                    section.getName(), e2);
                env.add("exception", e2.getMessage());
                reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                        "archive.badarchivetype", env));

                return reply;
            }
        }

        if (archiveType == null) {
            archiveType = archive.getArchiveType(section);
        }

        if (archiveTypeName == null) {
            archiveTypeName = archiveType.getClass().getName();
        }

        HashSet<RemoteSlave> slaveSet = new HashSet<RemoteSlave>();

        while (st.hasMoreTokens()) {
            String slavename = st.nextToken();

            try {
                RemoteSlave rslave = conn.getGlobalContext()
                                         .getSlaveManager()
                                         .getRemoteSlave(slavename);
                slaveSet.add(rslave);
            } catch (ObjectNotFoundException e2) {
                env.add("slavename", slavename);
                reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                        "archive.badslave", env));
            }
        }

        archiveType.setDirectory(dir);

        try {
            archive.checkPathForArchiveStatus(dir.getPath());
        } catch (DuplicateArchiveException e) {
            env.add("exception", e.getMessage());
            reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                    "archive.fail", env));
        }

        if (!slaveSet.isEmpty()) {
            archiveType.setRSlaves(slaveSet);
        }

        ArchiveHandler archiveHandler = new ArchiveHandler(archiveType);

        archiveHandler.start();
        env.add("dirname", dir.getPath());
        env.add("archivetypename", archiveTypeName);
        reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                "archive.success", env));

        return reply;
    }

    private void addConfig(Properties props, String string,
        SectionInterface section) {
        if (string.indexOf('=') == -1) {
            throw new IllegalArgumentException(string +
                " does not contain an = and is therefore not a property");
        }

        String[] data = string.split("=");

        if (data.length != 2) {
            throw new IllegalArgumentException(string +
                " is therefore not a property because it has no definite key");
        }

        if (props.containsKey(data[0])) {
            throw new IllegalArgumentException(string +
                " is already contained in the Properties");
        }

        props.put(section.getName() + "." + data[0], data[1]);
    }

    private Reply doLISTARCHIVETYPES(BaseFtpConnection conn) {
        Reply reply = new Reply(200);
        int x = 0;
        ReplacerEnvironment env = new ReplacerEnvironment();
        Archive archive;

        try {
            archive = (Archive) conn.getGlobalContext().getFtpListener(Archive.class);
        } catch (ObjectNotFoundException e) {
            reply.addComment(conn.jprintf(ArchiveCommandHandler.class,
                    "archive.loadarchive", env));

            return reply;
        }

        for (Iterator iter = archive.getArchiveHandlers().iterator();
                iter.hasNext(); x++) {
            ArchiveHandler archiveHandler = (ArchiveHandler) iter.next();
            reply.addComment(x + ". " + archiveHandler.getArchiveType());
        }

        return reply;
    }

    public String[] getFeatReplies() {
        return null;
    }

    public CommandHandler initialize(BaseFtpConnection conn,
        CommandManager initializer) {
        return this;
    }

    public void load(CommandManagerFactory initializer) {
    }

    public void unload() {
    }
}
