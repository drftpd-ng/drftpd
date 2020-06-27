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
package org.drftpd.master.commands.usermanagement;

import org.drftpd.common.dynamicdata.Key;

import java.util.Date;

/**
 * @author mog
 * @author zubov
 * @version $Id$
 */
public class UserManagement {

    public static final Key<String> TAGLINE = new Key<>(UserManagement.class, "tagline");

    public static final Key<Boolean> DEBUG = new Key<>(UserManagement.class, "debug");

    public static final Key<Float> RATIO = new Key<>(UserManagement.class, "ratio");

    public static final Key<Date> CREATED = new Key<>(UserManagement.class, "created");

    public static final Key<String> COMMENT = new Key<>(UserManagement.class, "comment");

    public static final Key<String> REASON = new Key<>(UserManagement.class, "reason");

    public static final Key<String> IRCIDENT = new Key<>(UserManagement.class, "ircident");

    public static final Key<Integer> MAXLOGINS = new Key<>(UserManagement.class, "maxlogins");

    public static final Key<Integer> MAXLOGINSIP = new Key<>(UserManagement.class, "maxloginsip");

    public static final Key<Integer> MAXSIMUP = new Key<>(UserManagement.class, "maxsimup");

    public static final Key<Integer> MAXSIMDN = new Key<>(UserManagement.class, "maxsimdn");

    public static final Key<Date> LASTSEEN = new Key<>(UserManagement.class, "lastseen");

    public static final Key<Long> WKLYALLOT = new Key<>(UserManagement.class, "wklyallot");

    public static final Key<Date> BANTIME = new Key<>(UserManagement.class, "bantime");

    public static final Key<String> BANREASON = new Key<>(UserManagement.class, "banreason");

}
