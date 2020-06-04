/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */

package org.drftpd.slave.diskselection.filter;

import org.drftpd.common.util.Bytes;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.slave.vfs.Root;

import java.util.Properties;

/**
 * /**
 *
 * <pre>
 *  X.filter=minfreespace
 *  X.multiplier=1
 *  X.minfreespace=1GB
 *  X.assign=1, 2
 * </pre>
 * <p>
 * Works like this: if(diskfree < minfreespace) {
 * addScore( -1 * ( (minfreespace - diskfree) * multiplier) )
 * }
 *
 * @author fr0w
 * @version $Id$
 */
public class MinfreespaceFilter extends DiskFilter {

    private final long _minfreespace;

    private final float _multiplier;

    public MinfreespaceFilter(DiskSelectionFilter diskSelection, Properties p, Integer i) {
        super(diskSelection, p, i);
        _minfreespace = Bytes.parseBytes(PropertyHelper.getProperty(p, i + ".minfreespace"));
        _multiplier = DiskFilter.parseMultiplier(p.getProperty(i + ".multiplier", "0"));
        _assignList = AssignRoot.parseAssign(this, p.getProperty(i + ".assign", "all"));
    }

    public void process(ScoreChart sc, String path) {
        AssignRoot.addScoresToChart(this, _assignList, sc);

        for (Root o : getRootList()) {
            if (!AssignRoot.isAssignedRoot(this, o, _assignList))
                continue;

            long df = o.getDiskSpaceAvailable();
            if (df < _minfreespace) {
                if (_multiplier == 0) {
                    sc.removeFromChart(o);
                } else {
                    sc.addScore(o, -(long) ((_minfreespace - df) * _multiplier));
                }
            }
        }
    }

    public String toString() {
        return getClass().getName() + "[minfreespace=" + _minfreespace + ",roots=" + getAssignList() + "]";
    }
}
