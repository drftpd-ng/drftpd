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
import java.util.Properties;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.master.RemoteSlave;
import org.drftpd.usermanager.User;
import org.drftpd.vfs.InodeHandleInterface;

/**
 * @author mog
 * @version $Id: SlavetopFilter.java 847 2004-12-02 03:32:41Z mog $
 */
public class SlavetopFilter extends Filter {
	private GlobalContext _gctx;

	private long _assign;

	private int _topslaves;

	public SlavetopFilter(FilterChain fc, int i, Properties p) {
		_gctx = fc.getGlobalContext();
		_topslaves = Integer.parseInt(PropertyHelper.getProperty(p, i
				+ ".topslaves"));
		_assign = Long.parseLong(PropertyHelper.getProperty(p, i + ".assign"));
	}

	public SlavetopFilter(GlobalContext gctx, int topslaves, long assign) {
		_gctx = gctx;
		_topslaves = topslaves;
		_assign = assign;
	}

	public void process(ScoreChart scorechart, User user, InetAddress peer,
			char direction, InodeHandleInterface dir, RemoteSlave sourceSlave) {
		process(scorechart, dir);
	}

	public void process(ScoreChart scorechart, InodeHandleInterface dir) {
		String path = dir.getPath();
		// I'll tackle this one later
		/*
		 * //// find the section part of the path name SectionInterface section =
		 * _gctx.getSectionManager().lookup(path); InodeHandle rls =
		 * section.getFirstDirInSection(dir);
		 * 
		 * Hashtable<RemoteSlave, ScoreChart.SlaveScore> slavesmap = new
		 * Hashtable<RemoteSlave, ScoreChart.SlaveScore>();
		 * 
		 * for (Iterator iter = scorechart.getSlaveScores().iterator();
		 * iter.hasNext();) { RemoteSlave rslave = ((ScoreChart.SlaveScore)
		 * iter.next()).getRSlave(); slavesmap.put(rslave, new
		 * ScoreChart.SlaveScore(rslave)); }
		 * 
		 * Collection files = LinkedRemoteFileUtils.getAllFiles(rls);
		 * 
		 * for (Iterator iter = files.iterator(); iter.hasNext();) {
		 * LinkedRemoteFileInterface file = (LinkedRemoteFileInterface)
		 * iter.next();
		 * 
		 * for (Iterator iterator = file.getSlaves().iterator();
		 * iterator.hasNext();) { RemoteSlave rslave = (RemoteSlave)
		 * iterator.next(); ScoreChart.SlaveScore score = (SlaveScore)
		 * slavesmap.get(rslave);
		 * 
		 * if (score == null) { continue; }
		 * 
		 * score.addScore(1); } }
		 * 
		 * ArrayList<ScoreChart.SlaveScore> slavescores = new ArrayList<ScoreChart.SlaveScore>(slavesmap.values());
		 * Collections.sort(slavescores, Collections.reverseOrder());
		 * 
		 * if(_assign == 0) { for(ScoreChart.SlaveScore score :
		 * slavescores.subList(_topslaves, slavescores.size())) {
		 * scorechart.removeSlaveScore(score.getRSlave()); } }
		 * 
		 * if (slavescores.get(0).getScore() == 0) { // No slaves win, no reason
		 * to assign points return; }
		 * 
		 * Iterator iter = slavescores.iterator(); for (int i = 0; i <
		 * _topslaves && iter.hasNext(); i++) { ScoreChart.SlaveScore score =
		 * (SlaveScore) iter.next();
		 * 
		 * try { scorechart.getSlaveScore(score.getRSlave()).addScore(_assign); }
		 * catch (ObjectNotFoundException e1) { throw new RuntimeException(e1); } }
		 */
	}
}
