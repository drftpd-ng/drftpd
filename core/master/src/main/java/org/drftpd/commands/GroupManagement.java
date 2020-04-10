/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.commands;

import org.drftpd.master.common.dynamicdata.Key;

import java.util.Date;

/**
 * @author mikevg
 * @version $Id$
 */
public class GroupManagement {

	public static final Key<Date> CREATED = new Key<>(GroupManagement.class, "created");

    public static final Key<Integer> GROUPSLOTS = new Key<>(GroupManagement.class, "groupslots");

    public static final Key<Integer> LEECHSLOTS = new Key<>(GroupManagement.class, "leechslots");

    public static final Key<Float> MINRATIO = new Key<>(GroupManagement.class, "minratio");

    public static final Key<Float> MAXRATIO = new Key<>(GroupManagement.class, "maxratio");

}
