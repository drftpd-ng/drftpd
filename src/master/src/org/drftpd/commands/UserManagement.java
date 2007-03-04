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

import java.util.Date;

import org.drftpd.dynamicdata.Key;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class UserManagement {

	public static final Key TAGLINE = new Key(UserManagement.class, "tagline",
			String.class);

	public static final Key DEBUG = new Key(UserManagement.class, "debug",
			Boolean.class);

	public static final Key RATIO = new Key(UserManagement.class, "ratio",
			Float.class);

	public static final Key CREATED = new Key(UserManagement.class, "created",
			Date.class);

	public static final Key COMMENT = new Key(UserManagement.class, "comment",
			String.class);

	public static final Key REASON = new Key(UserManagement.class, "reason",
			String.class);

	public static final Key IRCIDENT = new Key(UserManagement.class,
			"ircident", String.class);

	public static final Key GROUPSLOTS = new Key(UserManagement.class,
			"groupslots", Integer.class);

	public static final Key LEECHSLOTS = new Key(UserManagement.class,
			"leechslots", Integer.class);

	public static final Key MAXLOGINS = new Key(UserManagement.class,
			"maxlogins", Integer.class);

	public static final Key MAXLOGINSIP = new Key(UserManagement.class,
			"maxloginsip", Integer.class);

	public static final Key MINRATIO = new Key(UserManagement.class,
			"minratio", Float.class);

	public static final Key MAXRATIO = new Key(UserManagement.class,
			"maxratio", Float.class);

	public static final Key MAXSIMUP = new Key(UserManagement.class,
			"maxsimup", Integer.class);

	public static final Key MAXSIMDN = new Key(UserManagement.class,
			"maxsimdn", Integer.class);

	public static final Key LASTSEEN = new Key(UserManagement.class,
			"lastseen", Date.class);

	public static final Key WKLY_ALLOTMENT = new Key(UserManagement.class,
			"wkly_allotment", Long.class);

	public static final Key BAN_TIME = new Key(UserManagement.class,
			"ban_time", Date.class);

	public static final Key BAN_REASON = new Key(UserManagement.class,
			"ban_reason", String.class);
}
