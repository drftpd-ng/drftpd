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

import java.io.IOException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.TreeSet;

import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.FtpReply;
import net.sf.drftpd.master.command.plugins.Textoutput;
import net.sf.drftpd.master.command.CommandHandler;
import net.sf.drftpd.master.command.CommandManager;
import net.sf.drftpd.master.command.CommandManagerFactory;
import net.sf.drftpd.master.command.UnhandledCommandException;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;
import net.sf.drftpd.Bytes;

import org.apache.log4j.Logger;
import org.drftpd.sections.SectionInterface;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @version $Id: New.java,v 1.1 2004/03/28 15:50:38 zubov Exp $
 */
public class New implements CommandHandler {
	private static final Logger logger = Logger.getLogger(New.class);

	private class DateComparator implements Comparator {
		public int compare(Object o1, Object o2) {
			if (!(o1 instanceof LinkedRemoteFileInterface
				&& o2 instanceof LinkedRemoteFileInterface)) {
				throw new ClassCastException("Not a LinkedRemoteFile");
			}

			LinkedRemoteFileInterface f1 = (LinkedRemoteFileInterface) o1;
			LinkedRemoteFileInterface f2 = (LinkedRemoteFileInterface) o2;

			if (f1.lastModified() == f2.lastModified()) {
				return 0;
			}

			return f1.lastModified() > f2.lastModified() ? -1 : 1;
		}

		public boolean equals(Object o) {
			if (!(o instanceof DateComparator)) {
				return false;
			}

			return super.equals(o);
		}
	};

	public New() {
		super();
	}

	public FtpReply execute(BaseFtpConnection conn)
		throws UnhandledCommandException {
		FtpReply reply = new FtpReply(200);
		Collection sections =
			conn.getConnectionManager().getSectionManager().getSections();
		int count = 20;
		try {
			count = Integer.parseInt(conn.getRequest().getArgument());
		} catch (Exception e) {
			// Ignore and output the 20 freshest...  
		}

		// Collect all new files (= directories hopefully!) from all sections,
		// and sort them.

		Collection directories = new TreeSet(new DateComparator());
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
		long now = System.currentTimeMillis();
		for (Iterator iter = directories.iterator();
			iter.hasNext() && pos <= count;
			pos++) {
			LinkedRemoteFileInterface dir =
				(LinkedRemoteFileInterface) iter.next();
			env.add("pos", "" + pos);
			env.add("name", dir.getName());
			env.add("user", dir.getUsername());
			env.add("files", "" + dir.dirSize());
			env.add("size", Bytes.formatBytes(dir.length()));
			env.add("age", "" + formatAge(dir.lastModified(), now));
			reply.addComment(conn.jprintf(New.class, "new", env));
		}

		try {
			Textoutput.addTextToResponse(reply, "new_footer");
		} catch (IOException ioe) {
			logger.warn("Error reading new_footer", ioe);
		}

		return reply;
	}

	private static final String formatAge(long age, long now) {
		if (age >= now) {
			return "0m  0s";
		}

		// Less than an hour...  
		if (now - age < 60 * 60 * 1000) {
			long min = (now - age) / 60000;
			long s = ((now - age) - min * 60000) / 1000;
			return min + "m " + (s > 9 ? "" + s : " " + s) + "s";
		}

		// Less than a day... 
		if (now - age < 24 * 60 * 60 * 1000) {
			long h = (now - age) / (60 * 60000);
			long min = ((now - age) - h * 60 * 60000) / 60000;
			return h + "h " + (min > 9 ? "" + min : " " + min) + "m";
		}

		// Over a day...
		long d = (now - age) / (24 * 60 * 60000);
		long h = ((now - age) - d * 24 * 60 * 60000) / (60 * 60000);
		return d + "d " + (h > 9 ? "" + h : " " + h) + "h";
	}

	public CommandHandler initialize(
		BaseFtpConnection conn,
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
