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

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.config.FtpConfig;

import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.permissions.Permission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.drftpd.usermanager.User;

import com.Ostermiller.util.StringTokenizer;

/**
 * @author mog
 * @version $Id$
 */
public class UserFilter extends Filter {
    private Permission _perm;
	private ArrayList<MatchdirFilter.AssignSlave> _assigns;

	public UserFilter(FilterChain fc, int i, Properties p) throws ObjectNotFoundException {
    	_perm = new Permission(FtpConfig.makeUsers(new StringTokenizer(PropertyHelper.getProperty(p, i+".perm"))));
    	_assigns = MatchdirFilter.parseAssign(PropertyHelper.getProperty(p, i+".assign"), fc.getGlobalContext().getSlaveManager());
    }

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, LinkedRemoteFileInterface dir, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		MatchdirFilter.doAssign(_assigns, scorechart);
	}
}
