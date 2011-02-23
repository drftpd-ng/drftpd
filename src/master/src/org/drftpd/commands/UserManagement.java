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

	public static final Key<String> TAGLINE = new Key<String>(UserManagement.class, "tagline");

	public static final Key<Boolean> DEBUG = new Key<Boolean>(UserManagement.class, "debug");

	public static final Key<Float> RATIO = new Key<Float>(UserManagement.class, "ratio");

	public static final Key<Date> CREATED = new Key<Date>(UserManagement.class, "created");

	public static final Key<String> COMMENT = new Key<String>(UserManagement.class, "comment");

	public static final Key<String> REASON = new Key<String>(UserManagement.class, "reason");

	public static final Key<String> IRCIDENT = new Key<String>(UserManagement.class, "ircident");

	public static final Key<Integer> GROUPSLOTS = new Key<Integer>(UserManagement.class, "groupslots");

	public static final Key<Integer> LEECHSLOTS = new Key<Integer>(UserManagement.class, "leechslots");

	public static final Key<Integer> MAXLOGINS = new Key<Integer>(UserManagement.class,	"maxlogins");

	public static final Key<Integer> MAXLOGINSIP = new Key<Integer>(UserManagement.class, "maxloginsip");

	public static final Key<Float> MINRATIO = new Key<Float>(UserManagement.class, "minratio");

	public static final Key<Float> MAXRATIO = new Key<Float>(UserManagement.class, "maxratio");

	public static final Key<Integer> MAXSIMUP = new Key<Integer>(UserManagement.class, "maxsimup");

	public static final Key<Integer> MAXSIMDN = new Key<Integer>(UserManagement.class, "maxsimdn");

	public static final Key<Date> LASTSEEN = new Key<Date>(UserManagement.class, "lastseen");

	public static final Key<Long> WKLY_ALLOTMENT = new Key<Long>(UserManagement.class, "wkly_allotment");

	public static final Key<Date> BAN_TIME = new Key<Date>(UserManagement.class, "ban_time");

	public static final Key<String> BAN_REASON = new Key<String>(UserManagement.class, "ban_reason");

}
