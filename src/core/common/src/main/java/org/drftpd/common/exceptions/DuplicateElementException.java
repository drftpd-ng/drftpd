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

/**
 * @author mog
 * @version $Id$
 */
public class DuplicateElementException extends Exception {
    /**
     *
     */
    private static final long serialVersionUID = -3420616098497542539L;

    /**
     *
     */
    public DuplicateElementException() {
        super();
    }

    /**
     * @param message
     */
    public DuplicateElementException(String message) {
        super(message);
    }

    /**
     * @param message
     * @param cause
     */
    public DuplicateElementException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * @param cause
     */
    public DuplicateElementException(Throwable cause) {
        super(cause);
    }
}
