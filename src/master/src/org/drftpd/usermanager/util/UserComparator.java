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
package org.drftpd.usermanager.util;

import org.drftpd.usermanager.User;

import java.io.Serializable;
import java.util.Comparator;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
@SuppressWarnings("serial")
public class UserComparator implements Comparator<User>, Serializable {
    private String _type;

    public UserComparator(String type) {
        _type = type;
    }

    public int compare(User u1, User u2) {
        long thisVal = UserTransferStats.getStats(_type, u1);
        long anotherVal = UserTransferStats.getStats(_type, u2);
        return (Long.compare(anotherVal, thisVal));
    }
}
