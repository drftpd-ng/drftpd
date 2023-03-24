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
package org.drftpd.master.permissions;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author mog
 * @version $Id$
 */
public class MessagePathPermission extends StringPathPermission {
    private final ArrayList<String> _message;

    public MessagePathPermission(String pattern, String messageFile,
                                 Collection<String> users) throws IOException, java.io.FileNotFoundException {
        super(pattern, users);

        _message = new ArrayList<>();

        try (BufferedReader in = new BufferedReader(new FileReader(messageFile))) {
            String line;
            while ((line = in.readLine()) != null) {
                _message.add(line);
            }
        }

        _message.trimToSize();
    }

    public ArrayList<String> getMessage() {
        return _message;
    }
}
