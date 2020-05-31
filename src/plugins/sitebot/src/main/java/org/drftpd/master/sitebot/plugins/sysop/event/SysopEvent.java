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
package org.drftpd.master.sitebot.plugins.sysop.event;

public class SysopEvent {

    private final String _username;
    private final String _message;
    private final String _response;
    private final boolean _login;
    private final boolean _successful;

    public SysopEvent(String username, String message, String response, boolean login, boolean successful) {
        _username = username;
        _message = message;
        _response = response;
        _login = login;
        _successful = successful;
    }

    public String getUsername() {
        return _username;
    }

    public String getMessage() {
        return _message;
    }

    public String getResponse() {
        return _response;
    }

    public boolean isLogin() {
        return _login;
    }

    public boolean isSuccessful() {
        return _successful;
    }
}
