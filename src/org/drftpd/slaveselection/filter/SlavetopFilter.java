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

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;

import org.drftpd.remotefile.LinkedRemoteFileUtils;

import org.drftpd.sections.SectionInterface;

import org.drftpd.slaveselection.filter.ScoreChart.SlaveScore;

import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.Properties;


/**
 * @author mog
 * @version $Id: SlavetopFilter.java,v 1.14 2004/11/09 19:00:00 mog Exp $
 */
public class SlavetopFilter extends Filter {
    private GlobalContext _gctx;
    private long _assign;
    private int _topslaves;

    public SlavetopFilter(FilterChain fc, int i, Properties p) {
        _gctx = fc.getGlobalContext();
        _topslaves = Integer.parseInt(PropertyHelper.getProperty(p, i +
                    ".topslaves"));
        _assign = Long.parseLong(PropertyHelper.getProperty(p, i + ".assign"));
    }

    public SlavetopFilter(GlobalContext gctx, int topslaves, long assign) {
        _gctx = gctx;
        _topslaves = topslaves;
        _assign = assign;
    }

    public void process(ScoreChart scorechart, User user, InetAddress peer,
        char direction, LinkedRemoteFileInterface dir)
        throws NoAvailableSlaveException {
        String path = dir.getPath();

        //// find the section part of the path name
        SectionInterface section = _gctx.getSectionManager().lookup(path);

        LinkedRemoteFileInterface rls = section.getFirstDirInSection(dir);

        //			// string stuff
        //		if (section.getPath().endsWith("/")) // section is not the root dir - /
        //			path = path.substring(section.getPath().length());
        //		else path = path.substring(section.getPath().length()+1);
        //		int pos = path.indexOf('/');
        //		if (pos != -1)
        //			path = path.substring(0, pos);
        //		LinkedRemoteFileInterface rls;
        //		try {
        //			rls = section.getFile().getFile(path);
        //		} catch (FileNotFoundException e) {
        //			throw new RuntimeException(e);
        //		}
        Hashtable slavesmap = new Hashtable();

        for (Iterator iter = scorechart.getSlaveScores().iterator();
                iter.hasNext();) {
            RemoteSlave rslave = ((ScoreChart.SlaveScore) iter.next()).getRSlave();
            slavesmap.put(rslave, new ScoreChart.SlaveScore(rslave));
        }

        Collection files = LinkedRemoteFileUtils.getAllFiles(rls);

        for (Iterator iter = files.iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface file = (LinkedRemoteFileInterface) iter.next();

            for (Iterator iterator = file.getSlaves().iterator();
                    iterator.hasNext();) {
                RemoteSlave rslave = (RemoteSlave) iterator.next();
                ScoreChart.SlaveScore score = (SlaveScore) slavesmap.get(rslave);

                if (score == null) {
                    continue;
                }

                score.addScore(1);
            }
        }

        ArrayList slavescores = new ArrayList(slavesmap.values());
        Collections.sort(slavescores, Collections.reverseOrder());

        Iterator iter = slavescores.iterator();

        for (int i = 0; (i < _topslaves) && iter.hasNext(); i++) {
            ScoreChart.SlaveScore score = (SlaveScore) iter.next();

            try {
                scorechart.getSlaveScore(score.getRSlave()).addScore(_assign);
            } catch (ObjectNotFoundException e1) {
                throw new RuntimeException(e1);
            }
        }
    }
}
