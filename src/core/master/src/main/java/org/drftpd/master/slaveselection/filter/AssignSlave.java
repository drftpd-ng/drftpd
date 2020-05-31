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
package org.drftpd.master.slaveselection.filter;


import org.drftpd.master.GlobalContext;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.util.ArrayList;
import java.util.StringTokenizer;

public class AssignSlave {
    private static GlobalContext _gctx;

    /**
     * This method is needed by the TestCases.
     */
    public static GlobalContext getGlobalContext() {
        if (_gctx == null)
            _gctx = GlobalContext.getGlobalContext();
        return _gctx;
    }

    /**
     * This method is needed by the TestCases.
     *
     * @param gctx
     */
    public static void setGlobalContext(GlobalContext gctx) {
        _gctx = gctx;
    }

    public static ArrayList<AssignParser> parseAssign(String p) throws ObjectNotFoundException {
        StringTokenizer st = new StringTokenizer(p.replaceAll(",", ""));
        ArrayList<AssignParser> list = new ArrayList<>();

        while (st.hasMoreTokens()) {
            String toParse = st.nextToken();
            AssignParser ap = new AssignParser(toParse);
            list.add(ap);
        }

        list.trimToSize();
        return list;
    }

    public static void addScoresToChart(ArrayList<AssignParser> aps, ScoreChart sc) {
        for (AssignParser ap : aps) {
            // assign all slaves.
            if (ap.allAssigned()) {
                for (ScoreChart.SlaveScore score : sc.getSlaveScores()) {
                    score.addScore(ap.getScore());
                }

                return;
            }

            try {
                if (ap.isRemoved()) {
                    sc.removeSlaveFromChart(ap.getRSlave());
                } else {
                    sc.addScoreToSlave(ap.getRSlave(), ap.getScore());
                }
            } catch (ObjectNotFoundException e) {
                // slave is not in the scorechart, but that's np.
            }
        }
    }
}

class AssignParser {
    private long _score;

    private RemoteSlave _rslave;

    private boolean _all = false;

    private boolean _removed = false;

    public AssignParser(String s) throws ObjectNotFoundException {
        boolean positive;
        int pos = s.indexOf("+");

        if (pos != -1) {
            positive = true;
        } else {
            pos = s.indexOf("-");

            if (pos == -1) {
                throw new IllegalArgumentException(s + " is not a valid assign slave expression");
            }

            positive = false;
        }

        String assign = s.substring(pos + 1);
        String slavename = s.substring(0, pos);

        if (slavename.equalsIgnoreCase("all")) {
            _all = true;
            _score = Long.parseLong(assign);
            return;
        }

        try {
            _rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slavename);
        } catch (ObjectNotFoundException e) {
            throw new ObjectNotFoundException(slavename + " does not exist.", e);
        }

        if (assign.equals("remove")) {
            _score = Integer.MIN_VALUE;
            _removed = true;
            positive = false;
        } else {
            _score = Long.parseLong(assign);
            if (!positive) {
                _score = -_score;
            }
        }
    }

    public boolean isRemoved() {
        return _removed;
    }

    public RemoteSlave getRSlave() {
        return _rslave;
    }

    public long getScore() {
        return _score;
    }

    public String toString() {
        return getClass() + "@" + hashCode() + "[rslave=" + getRSlave().getName() + ",score=" + getScore() + "]";
    }

    public boolean allAssigned() {
        return _all;
    }
}
