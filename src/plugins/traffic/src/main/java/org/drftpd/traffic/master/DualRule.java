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
package org.drftpd.traffic.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.TransferState;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.slave.network.Transfer;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * One "dual" rule of traffic.conf. It is NOT a {@link TrafficType} registered in the manager, so it never
 * influences min/max speed of normal transfers; it only wraps an action type (default/kick/ban) which is
 * invoked when {@link DualCheck} finds a user dualing in a release.
 */
class DualRule {
    private static final Logger logger = LogManager.getLogger(DualRule.class);

    private final int _confnum;
    private final TrafficType _action;
    private final int _limit;
    private final long _cooldownMs;
    private final long _takeCredits;

    DualRule(Properties p, int confnum, TrafficType action) {
        _confnum = confnum;
        _action = action;

        if (action.getMinSpeed() <= 0) {
            throw new RuntimeException("Dual needs a positive " + confnum + ".minspeed - Skipping Config");
        }
        if (!action.getUpload() && !action.getDownload()) {
            throw new RuntimeException("Dual needs " + confnum + ".up and/or " + confnum + ".dn - Skipping Config");
        }
        if (p.getProperty(confnum + ".perms", "").trim().isEmpty()) {
            throw new RuntimeException("Dual needs " + confnum + ".perms (use * for everybody) - Skipping Config");
        }
        try {
            _limit = Integer.parseInt(p.getProperty(confnum + ".limit", "2").trim());
            _cooldownMs = Long.parseLong(p.getProperty(confnum + ".cooldown", "60").trim()) * 1000;
            _takeCredits = Long.parseLong(p.getProperty(confnum + ".takecredits", "0").trim()) * 1024L;
        } catch (NumberFormatException e) {
            throw new RuntimeException("Invalid number in " + confnum + ".limit/cooldown/takecredits - Skipping Config");
        }
        if (_limit < 2 || _cooldownMs < 0 || _takeCredits < 0) {
            throw new RuntimeException("Invalid " + confnum + ".limit/cooldown/takecredits - Skipping Config");
        }
    }

    /** True if this rule applies to the given user/directory (permissions, include, exclude). */
    private boolean applies(User user, String release) {
        return user != null && _action.checkInclude(release) && !_action.checkExclude(release)
                && _action.getPerms().check(user);
    }

    /** Builds a snapshot of every active transfer the master knows about (same source as SITE WHO). */
    static List<DualCheck.Sample> collect() {
        List<DualCheck.Sample> samples = new ArrayList<>();
        for (BaseFtpConnection conn : GlobalContext.getConnectionManager().getConnections()) {
            try {
                if (!conn.isAuthenticated()) {
                    continue;
                }
                User user;
                try {
                    user = conn.getUser();
                } catch (NoSuchUserException e) {
                    continue;
                }
                synchronized (conn) {
                    TransferState ts = conn.getTransferState();
                    synchronized (ts) {
                        if (!ts.isTransfering()) {
                            continue;
                        }
                        char dir = ts.getDirection();
                        if (dir != Transfer.TRANSFER_RECEIVING_UPLOAD && dir != Transfer.TRANSFER_SENDING_DOWNLOAD) {
                            continue;
                        }
                        FileHandle file = ts.getTransferFile();
                        samples.add(new DualCheck.Sample(user.getName(), file.getParent().getPath(),
                                dir == Transfer.TRANSFER_RECEIVING_UPLOAD, ts.getXferSpeed(), ts.getElapsed(),
                                new Object[]{conn, user, file, ts.getTransferSlave().getName(), ts.getTransfered()}));
                    }
                }
            } catch (RuntimeException e) {
                logger.debug("Skipping connection while looking for dual transfers", e);
            }
        }
        return samples;
    }

    /**
     * Runs this rule against the snapshot.
     *
     * @param handled   user/release/direction keys already actioned by an earlier rule in this scan
     * @param lastFired cooldown bookkeeping, shared between scans
     */
    void run(List<DualCheck.Sample> samples, Set<String> handled, Map<String, Long> lastFired, long now) {
        // A sample is exempt when this rule does not apply to it (perms/include/exclude)
        List<DualCheck.Violation> violations = DualCheck.detect(samples, _action.getMinSpeed(), _limit,
                _action.getUpload(), _action.getDownload(),
                s -> !applies((User) ((Object[]) s.getHandle())[1], s.getRelease()));

        for (DualCheck.Violation v : violations) {
            String key = v.getUser() + '\u0000' + v.getRelease() + '\u0000' + v.isStor();
            if (handled.contains(key)) {
                continue;
            }
            handled.add(key);
            Long last = lastFired.get(_confnum + key);
            if (last != null && now - last < _cooldownMs) {
                continue;
            }
            lastFired.put(_confnum + key, now);

            Object[] h = (Object[]) v.getTarget().getHandle();
            BaseFtpConnection conn = (BaseFtpConnection) h[0];
            User user = (User) h[1];
            FileHandle file = (FileHandle) h[2];
            logger.info("Dual transfer: user {} has {} simultaneous {} in {} (rule {}); applying {}",
                    v.getUser(), v.getSamples().size(), v.isStor() ? "uploads" : "downloads", v.getRelease(),
                    _confnum, _action.getType());
            try {
                if (_takeCredits > 0) {
                    user.updateCredits(-_takeCredits);
                    user.commit();
                }
                _action.doAction(user, file, v.isStor(), _action.getMinSpeed(), v.getTarget().getSpeed(),
                        (Long) h[4], conn, (String) h[3]);
            } catch (RuntimeException e) {
                logger.error("Failed to apply dual action for rule {}", _confnum, e);
            }
        }
    }
}
