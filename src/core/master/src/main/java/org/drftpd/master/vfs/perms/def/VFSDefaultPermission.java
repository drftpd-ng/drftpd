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
package org.drftpd.master.vfs.perms.def;

import org.drftpd.master.permissions.GlobPathPermission;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.vfs.perms.VFSPermHandler;

import java.util.Map;
import java.util.StringTokenizer;
import java.util.regex.PatternSyntaxException;

/**
 * Default handler. Uses Glob patterns.
 *
 * @author fr0w
 * @version $Id$
 */
public class VFSDefaultPermission extends VFSPermHandler {
    public void handle(String directive, StringTokenizer st) throws PatternSyntaxException {
        addPermission(directive, new GlobPathPermission(st.nextToken(), Permission.makeUsers(st)));
    }

    @Override
    public Map<String, String> getDirectives() {
        return Map.of("upload", "upload",
                "makedir", "makedir",
                "delete", "delete",
                "deleteown", "deleteown",
                "rename", "rename",
                "renameown", "renameown",
                "privpath", "privpath",
                "download", "download");
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
