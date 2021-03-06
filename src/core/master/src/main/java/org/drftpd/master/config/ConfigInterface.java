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
package org.drftpd.master.config;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.dynamicdata.KeyedMap;
import org.drftpd.common.util.PortRange;
import org.drftpd.master.permissions.PathPermission;
import org.drftpd.master.permissions.Permission;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.perms.VFSPermissions;

import java.net.InetAddress;
import java.util.List;
import java.util.Properties;

/**
 * @author mog
 * @author fr0w
 * @version $Id$
 */
public interface ConfigInterface {
    KeyedMap<Key<?>, Object> getKeyedMap();

    Properties getMainProperties();

    VFSPermissions getVFSPermissions();

    void reload();

    boolean checkPathPermission(String directive, User fromUser, DirectoryHandle path);

    boolean checkPathPermission(String directive, User fromUser, DirectoryHandle path, boolean defaults);

    boolean checkPermission(String directive, User user);

    void addPathPermission(String directive, PathPermission permission);

    void addPermission(String directive, Permission permission);

    List<InetAddress> getBouncerIps();

    boolean getHideIps();

    String getLoginPrompt();

    String getAllowConnectionsDenyReason();

    int getMaxUsersExempt();

    int getMaxUsersTotal();

    boolean isLoginAllowed(User user);

    boolean isLoginExempt(User user);

    PortRange getPortRange();

    String getPasvAddress() throws NullPointerException;

    String[] getCipherSuites();

    String[] getSSLProtocols();

    String getHideInStats();

    String getDefaultPreUser();

    String getDefaultPreGroup();
}
