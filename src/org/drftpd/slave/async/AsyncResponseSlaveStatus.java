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
package org.drftpd.slave.async;

import net.sf.drftpd.slave.SlaveStatus;


/**
 * @author zubov
 * @version $Id: AsyncResponseSlaveStatus.java,v 1.4 2004/11/08 18:39:31 mog Exp $
 */
public class AsyncResponseSlaveStatus extends AsyncResponse {
    private SlaveStatus _status;

    public AsyncResponseSlaveStatus(SlaveStatus status) {
        this("SlaveStatus", status);
    }

    public AsyncResponseSlaveStatus(String index, SlaveStatus status) {
        super(index);

        if (status == null) {
            throw new IllegalArgumentException("status cannot be null");
        }

        _status = status;
    }

    public SlaveStatus getSlaveStatus() {
        return _status;
    }

    public String toString() {
        return getClass().getName() + "[status=" + getSlaveStatus() + "]";
    }
}
