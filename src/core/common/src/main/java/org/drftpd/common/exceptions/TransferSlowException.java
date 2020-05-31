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
package org.drftpd.common.exceptions;

import org.drftpd.common.slave.TransferStatus;

/**
 * @author CyBeR
 * @version $Id: TransferSlowException.java 1945 2009-07-25 18:32:01Z djb61 $
 */
@SuppressWarnings("serial")
public class TransferSlowException extends TransferFailedException {
    public TransferSlowException(Exception e, TransferStatus status) {
        super(e.getMessage(), status);
    }

    public TransferSlowException(String message, TransferStatus status) {
        super(message, status);
    }
}
