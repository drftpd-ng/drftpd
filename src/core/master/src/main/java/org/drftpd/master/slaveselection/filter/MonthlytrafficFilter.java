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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.common.vfs.InodeHandleInterface;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.event.SlaveEvent;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.usermanager.User;

import java.net.InetAddress;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-slave monthly traffic quota with a soft (warn) and a hard limit.
 * <p>
 * The traffic is taken from the per-slave month counters ({@link RemoteSlave#getUploadedBytesMonth()}
 * and {@link RemoteSlave#getDownloadedBytesMonth()}), which are reset at the start of each month.
 * Opt-in: nothing changes unless this filter is listed in a slaveselection-*.conf.
 *
 * <pre>
 *  &lt;n&gt;.filter=monthlytraffic
 *  &lt;n&gt;.limit=500GB            (default hard limit for slaves without their own limit, optional)
 *  &lt;n&gt;.limit.&lt;slavename&gt;=2TB (hard limit for one slave, optional)
 *  &lt;n&gt;.warn=80%               (soft limit, a percentage of the limit or bytes, e.g. 400GB, optional)
 *  &lt;n&gt;.direction=both         (up, down or both: which month counters count towards the quota)
 *  &lt;n&gt;.multiplier=1           (soft part, see below)
 * </pre>
 * <p>
 * Works like this, with <code>used</code> being the counted traffic of the slave this month:
 * <ul>
 * <li>no limit configured for the slave: the slave is not touched</li>
 * <li>used &gt;= limit: the slave is removed from the candidates</li>
 * <li>warn &lt;= used &lt; limit: addScore( -1 * (used - warn) * multiplier ), or the slave is removed
 * if the multiplier is "remove" (0)</li>
 * </ul>
 * The first time a slave reaches the warn and the hard level an event is published for the
 * sitebot (announce type "slavequota"). The event is only sent again after the usage fell below the level
 * (new month) and reached it again, or after a master restart.
 */
public class MonthlytrafficFilter extends Filter {
    private static final Logger logger = LogManager.getLogger(MonthlytrafficFilter.class);

    static final int LEVEL_OK = 0;
    static final int LEVEL_WARN = 1;
    static final int LEVEL_LIMIT = 2;

    /**
     * Last seen level per slave, counter selection and limit, shared between all instances (up/down chains, reloads).
     */
    private static final Map<String, Integer> _levels = new ConcurrentHashMap<>();

    private final long _defaultLimit;
    private final Properties _p;
    private final int _i;
    private final String _warn;
    private final boolean _warnPercent;
    private final long _warnValue;
    private final boolean _up;
    private final boolean _down;
    private final float _multiplier;

    public MonthlytrafficFilter(int i, Properties p) {
        super(i, p);
        _i = i;
        _p = p;
        String limit = p.getProperty(i + ".limit");
        _defaultLimit = limit == null ? -1 : parseLimit(limit.trim(), i + ".limit");

        String warn = p.getProperty(i + ".warn");
        _warn = warn == null ? null : warn.trim();
        if (_warn == null) {
            _warnPercent = false;
            _warnValue = -1;
        } else if (_warn.endsWith("%")) {
            _warnPercent = true;
            double pct;
            try {
                pct = Double.parseDouble(_warn.substring(0, _warn.length() - 1));
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException(i + ".warn is not a valid percentage: " + _warn, e);
            }
            if (pct <= 0 || pct > 100) {
                throw new IllegalArgumentException(i + ".warn must be above 0% and at most 100%: " + _warn);
            }
            _warnValue = Math.round(pct * 100);  // hundredths of a percent
        } else {
            _warnPercent = false;
            _warnValue = parseLimit(_warn, i + ".warn");
        }

        String dir = p.getProperty(i + ".direction", "both").trim().toLowerCase();
        switch (dir) {
            case "up" -> {
                _up = true;
                _down = false;
            }
            case "down" -> {
                _up = false;
                _down = true;
            }
            case "both" -> {
                _up = true;
                _down = true;
            }
            default -> throw new IllegalArgumentException(i + ".direction must be up, down or both: " + dir);
        }

        String mult = p.getProperty(i + ".multiplier");
        _multiplier = mult == null ? 1 : parseMultiplier(mult.trim());

        boolean hasSlaveLimit = false;
        for (String key : p.stringPropertyNames()) {
            if (key.startsWith(i + ".limit.")) {
                hasSlaveLimit = true;
                parseLimit(p.getProperty(key).trim(), key);
            }
        }
        if (_defaultLimit < 0 && !hasSlaveLimit) {
            logger.warn("monthlytraffic filter {} has no {}.limit or {}.limit.<slave> configured, it does nothing",
                    i, i, i);
        }
    }

    private static long parseLimit(String value, String key) {
        long l;
        try {
            l = Bytes.parseBytes(value);
        } catch (NumberFormatException | StringIndexOutOfBoundsException e) {
            throw new IllegalArgumentException(key + " is not a valid size: " + value, e);
        }
        if (l <= 0) {
            throw new IllegalArgumentException(key + " must be positive: " + value);
        }
        return l;
    }

    /**
     * @return the hard limit of the slave in bytes or -1 if the slave has no limit
     */
    long getLimit(String slaveName) {
        String v = _p.getProperty(_i + ".limit." + slaveName);
        return v != null ? parseLimit(v.trim(), _i + ".limit." + slaveName) : _defaultLimit;
    }

    /**
     * @return the soft limit in bytes for the given hard limit, or -1 if there is no soft limit
     */
    long getWarn(long limit) {
        if (_warn == null) {
            return -1;
        }
        long warn = _warnPercent ? (long) ((double) limit * _warnValue / 10000.0) : _warnValue;
        return Math.min(warn, limit);
    }

    long getUsed(RemoteSlave slave) {
        long used = 0;
        if (_up) {
            used += slave.getUploadedBytesMonth();
        }
        if (_down) {
            used += slave.getDownloadedBytesMonth();
        }
        return used;
    }

    /**
     * @return LEVEL_OK, LEVEL_WARN or LEVEL_LIMIT
     */
    int getLevel(long used, long limit) {
        if (used >= limit) {
            return LEVEL_LIMIT;
        }
        long warn = getWarn(limit);
        return warn >= 0 && used >= warn ? LEVEL_WARN : LEVEL_OK;
    }

    @Override
    public void process(ScoreChart scorechart, User user, InetAddress source,
                        char direction, InodeHandleInterface file, RemoteSlave sourceSlave) {
        for (Iterator<ScoreChart.SlaveScore> iter = scorechart.getSlaveScores().iterator(); iter.hasNext(); ) {
            ScoreChart.SlaveScore score = iter.next();
            RemoteSlave slave = score.getRSlave();
            long limit = getLimit(slave.getName());
            if (limit < 0) {
                continue;
            }

            long used = getUsed(slave);
            int level = getLevel(used, limit);
            track(slave, level, used, limit);

            if (level == LEVEL_LIMIT) {
                iter.remove();
            } else if (level == LEVEL_WARN) {
                if (_multiplier == 0) {
                    iter.remove();
                } else {
                    score.addScore(-(long) ((used - getWarn(limit)) * _multiplier));
                }
            }
        }
    }

    private void track(RemoteSlave slave, int level, long used, long limit) {
        Integer previous = _levels.put(slave.getName() + "|" + (_up ? "u" : "") + (_down ? "d" : "") + "|" + limit, level);
        if (level > (previous == null ? LEVEL_OK : previous)) {
            String msg = (level == LEVEL_LIMIT ? "reached its monthly traffic limit: " : "is close to its monthly traffic limit: ")
                    + Bytes.formatBytes(used) + " of " + Bytes.formatBytes(limit) + " used";
            logger.info("Slave {} {}", slave.getName(), msg);
            announce(slave, level, msg);
        }
    }

    /**
     * Publishes the event the sitebot announces ("slavequota" announce type).
     */
    void announce(RemoteSlave slave, int level, String message) {
        if (GlobalContext.getEventService() != null) {
            GlobalContext.getEventService().publishAsync(
                    new SlaveEvent(level == LEVEL_LIMIT ? "SLAVEQUOTALIMIT" : "SLAVEQUOTAWARN", message, slave));
        }
    }

    static void resetNotifications() {
        _levels.clear();
    }
}
