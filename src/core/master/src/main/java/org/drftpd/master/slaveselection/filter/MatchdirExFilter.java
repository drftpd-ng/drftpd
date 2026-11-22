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

import org.drftpd.common.util.PropertyHelper;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.exceptions.FatalException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Properties;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Example slaveselection entry:
 *
 * <pre>
 *  &lt;n&gt;.filter=matchdirex
 *  &lt;n&gt;.assign=&lt;slavename&gt;+100000
 *  &lt;n&gt;.match=&lt;path regex match&gt;
 *  &lt;n&gt;.assume.remove=&lt;true&gt;
 *  &lt;n&gt;.negate.expression=&lt;true&gt;
 * </pre>
 *
 * @author scitz0
 * @version $Id$
 */
public class MatchdirExFilter extends Filter {
    private final ArrayList<AssignParser> _assigns;

    private final Pattern _p;

    private final boolean _negateExpr;

    // Names of slaves mentioned in assign, or null if non-assigned slaves are not to be removed.
    private final Set<String> _assignedSlaves;

    public MatchdirExFilter(int i, Properties p) {
        super(i, p);
        try {
            _assigns = AssignSlave.parseAssign(PropertyHelper.getProperty(p, i + ".assign"));

            // If assume.remove=true, every slave not mentioned in assign is removed in process(),
            // so slaves added after the config was loaded are removed as well.
            boolean assumeRemove = PropertyHelper.getProperty(p, i + ".assume.remove", "false").
                    equalsIgnoreCase("true");
            _assignedSlaves = assumeRemove ? AssignSlave.getAssignedSlaveNames(_assigns) : null;

            _p = Pattern.compile(PropertyHelper.getProperty(p, i + ".match"),
                    Pattern.CASE_INSENSITIVE);

            _negateExpr = PropertyHelper.getProperty(p, i + ".negate.expression", "false").
                    equalsIgnoreCase("true");
        } catch (Exception e) {
            throw new FatalException(e);
        }
    }

    public void process(ScoreChart scorechart, User user, InetAddress source,
                        char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
        Matcher m = _p.matcher(file.getPath());
        boolean validPath = _negateExpr != m.find();
        if (validPath) {
            AssignSlave.addScoresToChart(_assigns, scorechart);
            AssignSlave.removeSlavesNotIn(_assignedSlaves, scorechart);
        }
    }
}