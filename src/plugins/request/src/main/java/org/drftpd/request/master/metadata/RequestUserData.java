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
package org.drftpd.request.master.metadata;

import org.drftpd.common.dynamicdata.Key;

import java.io.Serializable;

/**
 * @author scitz0
 * @version $id$
 */
public class RequestUserData implements Serializable {

    public static final Key<Integer> REQUESTSFILLED = new Key<>(RequestUserData.class, "requestsFilled");
    public static final Key<Integer> REQUESTS = new Key<>(RequestUserData.class, "requests");
    public static final Key<Integer> WEEKREQS = new Key<>(RequestUserData.class, "weekReqs");
    public static final Key<String> RENUSER_FROM = new Key<>(RequestUserData.class, "renuser_from");
    public static final Key<String> RENUSER_TO = new Key<>(RequestUserData.class, "renuser_to");

}
