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

import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.oro.text.GlobCompiler;
import org.apache.oro.text.regex.Pattern;
import org.apache.oro.text.regex.Perl5Matcher;

import org.drftpd.usermanager.User;

import java.net.InetAddress;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;


/**
 * Example slaveselection entry:
 * <pre>
 * <n>.filter=matchdir
 * <n>.assignslave=<slavename>+100000
 * <n>.match=<path glob match>
 * </pre>
 *
 * @author mog
 * @version $Id: MatchdirFilter.java,v 1.7 2004/11/03 16:46:48 mog Exp $
 */
public class MatchdirFilter extends Filter {
    private ArrayList _assigns;
    private FilterChain _ssm;
    private Pattern _p;
    private Perl5Matcher _m = new Perl5Matcher();

    public MatchdirFilter(FilterChain ssm, int i, Properties p) {
        _ssm = ssm;

        try {
            parseAssign(FtpConfig.getProperty(p, i + ".assign"));
            _p = new GlobCompiler().compile(FtpConfig.getProperty(p,
                        i + ".match"));
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }

            throw new RuntimeException(e);
        }
    }

    private void parseAssign(String assign) throws ObjectNotFoundException {
        StringTokenizer st = new StringTokenizer(assign, ", ");
        ArrayList assigns = new ArrayList();

        while (st.hasMoreTokens()) {
            assigns.add(new AssignSlave(st.nextToken(),
                    _ssm.getGlobalContext().getSlaveManager()));
        }

        _assigns = assigns;
    }

    private void doAssign(ScoreChart scorechart) {
        for (Iterator iter = _assigns.iterator(); iter.hasNext();) {
            AssignSlave assign = (AssignSlave) iter.next();

            if (assign.isAll()) {
                for (Iterator iterator = scorechart.getSlaveScores().iterator();
                        iterator.hasNext();) {
                    ScoreChart.SlaveScore score = (ScoreChart.SlaveScore) iterator.next();

                    if (assign.getScore() == 0) {
                        iterator.remove();
                    } else {
                        score.addScore(assign.getScore());
                    }
                }
            } else {
                if (assign.getScore() == 0) {
                    scorechart.removeSlaveScore(assign.getRSlave());
                } else {
                    try {
                        scorechart.getSlaveScore(assign.getRSlave()).addScore(assign.getScore());

                        //not in scorechart, this is OK
                    } catch (ObjectNotFoundException e) {
                    }
                }
            }
        }
    }

    public void process(ScoreChart scorechart, User user, InetAddress source,
        char direction, LinkedRemoteFileInterface file) {
        if (_m.matches(file.getPath(), _p)) {
            doAssign(scorechart);
        }
    }

    private static class AssignSlave {
        private RemoteSlave _rslave;
        private long _score;

        public AssignSlave(String s, SlaveManager slaveManager)
            throws ObjectNotFoundException {
            boolean isAdd;
            int pos = s.indexOf("+");

            if (pos != -1) {
                isAdd = true;
            } else {
                pos = s.indexOf("-");

                if (pos == -1) {
                    throw new IllegalArgumentException(s +
                        " is not a valid assign slave expression");
                }

                isAdd = false;
            }

            String slavename = s.substring(0, pos);

            if (!slavename.equalsIgnoreCase("all")) {
                _rslave = slaveManager.getRemoteSlave(slavename);
            }

            String assign = s.substring(pos + 1);

            if (assign.equals("remove")) {
                _score = 0;
                isAdd = false;
            } else {
                _score = Long.parseLong(s.substring(pos + 1));

                if (!isAdd) {
                    _score = -_score;
                }
            }
        }

        public RemoteSlave getRSlave() {
            return _rslave;
        }

        public long getScore() {
            return _score;
        }

        public boolean isAll() {
            return _rslave == null;
        }
    }
}
