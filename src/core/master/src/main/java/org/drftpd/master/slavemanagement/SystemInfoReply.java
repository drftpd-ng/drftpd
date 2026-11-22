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
package org.drftpd.master.slavemanagement;

import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.slave.SystemInfo;
import org.drftpd.slave.network.AsyncResponseSystemInfo;
import org.drftpd.slave.protocol.SlaveProtocolCentral;

/**
 * Outcome of a "sysinfo" request to a slave. A request never drops the slave offline: every failure mode
 * is reported here instead.
 */
public final class SystemInfoReply {

    public enum Status {
        /** The slave answered, {@link #getSystemInfo()} is set. */
        OK,
        /** The slave runs an older version without a "sysinfo" handler. */
        UNSUPPORTED,
        /** The slave is offline (or went offline while waiting). */
        OFFLINE,
        /** No answer within the timeout. */
        TIMEOUT,
        /** The slave answered with an unexpected error. */
        ERROR
    }

    private final Status _status;
    private final SystemInfo _systemInfo;
    private final String _message;

    private SystemInfoReply(Status status, SystemInfo systemInfo, String message) {
        _status = status;
        _systemInfo = systemInfo;
        _message = message;
    }

    public static SystemInfoReply ok(SystemInfo systemInfo) {
        return new SystemInfoReply(Status.OK, systemInfo, null);
    }

    public static SystemInfoReply of(Status status, String message) {
        return new SystemInfoReply(status, null, message);
    }

    /**
     * Interprets the response of a slave to the "sysinfo" command.
     * <p>
     * A slave that has no handler for a command answers with an {@link AsyncResponseException} wrapping a plain
     * {@link Exception} whose message is {@code "<command>" + SlaveProtocolCentral.OPERATION_NOT_SUPPORTED}
     * (see {@link SlaveProtocolCentral#handleCommand}); this has been the behaviour of every 4.x slave.
     */
    public static SystemInfoReply fromResponse(AsyncResponse response) {
        if (response instanceof AsyncResponseSystemInfo) {
            SystemInfo si = ((AsyncResponseSystemInfo) response).getSystemInfo();
            if (si == null) {
                return of(Status.ERROR, "empty reply");
            }
            return ok(si);
        }
        if (response instanceof AsyncResponseException) {
            Throwable t = ((AsyncResponseException) response).getThrowable();
            if (isUnsupportedOperation(t)) {
                return of(Status.UNSUPPORTED, t.getMessage());
            }
            return of(Status.ERROR, t == null ? "unknown error" : t.getClass().getSimpleName());
        }
        return of(Status.ERROR, "unexpected reply " + (response == null ? "null" : response.getClass().getSimpleName()));
    }

    static boolean isUnsupportedOperation(Throwable t) {
        return t != null && t.getMessage() != null
                && t.getMessage().endsWith(SlaveProtocolCentral.OPERATION_NOT_SUPPORTED);
    }

    public Status getStatus() {
        return _status;
    }

    public SystemInfo getSystemInfo() {
        return _systemInfo;
    }

    public String getMessage() {
        return _message;
    }

    public String toString() {
        return "SystemInfoReply[status=" + _status + ",message=" + _message + "]";
    }
}
