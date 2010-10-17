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
package org.drftpd.slaveselection.filter;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.drftpd.PropertyHelper;
import org.drftpd.exceptions.FatalException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Properties;

/**
 * Example slaveselection entry:
 * 
 * <pre>
 *  &lt;n&gt;.filter=matchdirregex
 *  &lt;n&gt;.assign=&lt;slavename&gt;+100000
 *  &lt;n&gt;.match=&lt;path regex match&gt;
 * </pre>
 * 
 * @author scitz0
 * @version $Id$
 */
public class MatchdirRegexFilter extends Filter {
	private ArrayList<AssignParser> _assigns;

	private Pattern _p;

	public MatchdirRegexFilter(int i, Properties p) {
		super(i, p);
		try {
			_assigns = AssignSlave.parseAssign(PropertyHelper.getProperty(p, i + ".assign"));
			_p = Pattern.compile(PropertyHelper.getProperty(p, i	+ ".match"),
					Pattern.CASE_INSENSITIVE);
		} catch (Exception e) {
			throw new FatalException(e);
		}
	}

	public void process(ScoreChart scorechart, User user, InetAddress source,
			char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
		Matcher m = _p.matcher(file.getPath());
		if (m.find()) {
			AssignSlave.addScoresToChart(_assigns, scorechart);
		}
	}
}