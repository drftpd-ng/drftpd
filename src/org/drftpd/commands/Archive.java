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
import java.util.ArrayList;
import java.util.StringTokenizer;

import org.drftpd.mirroring.ArchiveHandler;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.plugins.SiteBot;
import org.drftpd.sections.SectionInterface;
import org.tanesha.replacer.ReplacerEnvironment;

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/*
 * @author zubov
 * @version $Id
 */
public class Archive implements CommandHandler {

	public Archive() {
		super();
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpReply reply = new FtpReply(200);
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		if (!conn.getRequest().hasArgument()) {
			reply.addComment(conn.jprintf(Archive.class, "archive.usage", env));
			return reply;
		}
		StringTokenizer st =
			new StringTokenizer(conn.getRequest().getArgument());
		if (st.countTokens() < 2) {
			reply.addComment(conn.jprintf(Archive.class, "archive.usage", env));
			return reply;
		}
		String archiveTypeName = st.nextToken();
		ArchiveType archiveType = null;
		try {
			archiveType =
				(ArchiveType) Class
					.forName(
						"org.drftpd.mirroring.archivetypes." + archiveTypeName)
					.newInstance();
		} catch (Exception e) {
			reply.addComment(conn.jprintf(Archive.class, "archive.usage", env));
			env.add("archivetypename", archiveTypeName);
			reply.addComment(
				conn.jprintf(Archive.class, "archive.badarchivetype", env));
			return reply;
		}
		String dirname = st.nextToken();
		LinkedRemoteFileInterface lrf;
		try {
			lrf = conn.getCurrentDirectory().getFile(dirname);
		} catch (FileNotFoundException e1) {
			try {
				lrf = conn.getConnectionManager().getRoot().lookupFile(dirname);
			} catch (FileNotFoundException e2) {
				reply.addComment(
					conn.jprintf(Archive.class, "archive.usage", env));
				env.add("dirname", dirname);
				reply.addComment(
					conn.jprintf(Archive.class, "archive.baddir", env));
				return reply;
			}
		}
		ArrayList slaveList = new ArrayList();
		while (st.hasMoreTokens()) {
			String slavename = st.nextToken();
			try {
				RemoteSlave rslave =
					conn.getConnectionManager().getSlaveManager().getSlave(
						slavename);
				slaveList.add(rslave);
			} catch (ObjectNotFoundException e2) {
				env.add("slavename", slavename);
				reply.addComment(
					conn.jprintf(Archive.class, "archive.badslave", env));
			}
		}
		net.sf.drftpd.event.listeners.Archive archive;
		try {
			archive =
				(net.sf.drftpd.event.listeners.Archive) conn
					.getConnectionManager()
					.getFtpListener(net.sf.drftpd.event.listeners.Archive.class);
		} catch (ObjectNotFoundException e3) {
			reply.addComment(
				conn.jprintf(Archive.class, "archive.loadarchive", env));
			return reply;
		}
		SectionInterface section =
			conn.getConnectionManager().getSectionManager().lookup(
				lrf.getPath());
		archiveType.init(archive, section);
		archiveType.setDirectory(lrf);
		if (!slaveList.isEmpty())
			archiveType.setRSlaves(slaveList);
		ArchiveHandler archiveHandler = new ArchiveHandler(archiveType);
		archiveHandler.start();
		env.add("dirname", lrf.getPath());
		env.add("archivetypename", archiveTypeName);
		reply.addComment(conn.jprintf(Archive.class, "archive.success", env));
		return reply;
	}

	public String[] getFeatReplies() {
		return null;
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
		CommandManager initializer) {
		return this;
	}

	public void load(CommandManagerFactory initializer) {
	}

	public void unload() {
	}

}
