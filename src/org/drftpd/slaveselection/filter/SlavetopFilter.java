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

import java.io.FileNotFoundException;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.remotefile.LinkedRemoteFileUtils;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;

/**
 * @author mog
 * @version $Id: SlavetopFilter.java,v 1.2 2004/02/27 01:02:21 mog Exp $
 */
public class SlavetopFilter extends Filter {

	private long _assign;

	private FilterChain _ssm;

	private int _topslaves;

	public SlavetopFilter(FilterChain ssm, int i, Properties p) {
		_ssm = ssm;
		_topslaves =
			Integer.parseInt(FtpConfig.getProperty(p, i + ".topslaves"));
		_assign = Long.parseLong(FtpConfig.getProperty(p, i + ".assign"));
	}

	public void process(
		ScoreChart scorechart,
		User user,
		InetAddress peer,
		char direction,
		LinkedRemoteFileInterface dir)
		throws NoAvailableSlaveException {

		String path = dir.getPath();
		SectionInterface section =
			_ssm
				.getSlaveManager()
				.getConnectionManager()
				.getSectionManager()
				.lookup(
				path);

		path = path.substring(path.length());
		path = path.substring(0, path.indexOf('/'));
		LinkedRemoteFileInterface rls;
		try {
			rls = section.getFile().getFile(path);
		} catch (FileNotFoundException e) {
			throw new RuntimeException(e);
		}

		Hashtable slavesmap = new Hashtable();
		for (Iterator iter =
			_ssm.getSlaveManager().getAvailableSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			slavesmap.put(rslave, new ScoreChart.SlaveScore(rslave));
		}

		Collection files = LinkedRemoteFileUtils.getAllFiles(rls);
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFileInterface file =
				(LinkedRemoteFileInterface) iter.next();
			for (Iterator iterator = file.getSlaves().iterator();
				iterator.hasNext();
				) {
				RemoteSlave rslave = (RemoteSlave) iterator.next();
				ScoreChart.SlaveScore score =
					(SlaveScore) slavesmap.get(rslave);
				if (score == null)
					continue;
				score.addScore(1);
			}
		}

		ArrayList slavescores = new ArrayList(slavesmap.values());
		Collections.sort(slavescores);
		Iterator iter = slavescores.iterator();
		for (int i = 0; i < _topslaves && iter.hasNext(); i++) {
			try {
				scorechart.getSlaveScore((RemoteSlave) iter.next()).addScore(
					_assign);
			} catch (ObjectNotFoundException e1) {
				continue;
			}
		}
	}
}
