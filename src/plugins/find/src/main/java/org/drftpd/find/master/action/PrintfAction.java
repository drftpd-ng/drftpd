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
package org.drftpd.find.master.action;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.Bytes;
import org.drftpd.find.master.FindUtils;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * @author scitz0
 * @version $Id$
 */
public class PrintfAction implements ActionInterface {
    public static final Logger logger = LogManager.getLogger(PrintfAction.class);

    private String _format;

    @Override
    public String name() {
        return "Printf";
    }

    @Override
    public void initialize(String action, String[] args) throws ImproperUsageException {
        if (args == null) {
            throw new ImproperUsageException("Missing argument for " + action + " action");
        }
        _format = FindUtils.getStringFromArray(args, " ");
    }

    @Override
    public String exec(CommandRequest request, InodeHandle inode) {
        return formatOutput(inode);
    }

    private String formatOutput(InodeHandle inode) {

        HashMap<String, String> formats = new HashMap<>();

        try {
            logger.debug("printf name: {}", inode.getName());
            formats.put("#f", inode.getName());
            formats.put("#p", inode.getPath());
            formats.put("#s", Bytes.formatBytes(inode.getSize()));
            formats.put("#u", inode.getUsername());
            formats.put("#g", inode.getGroup());
            formats.put("#t", new Date(inode.lastModified()).toString());

            if (inode.isFile())
                formats.put("#x", ((FileHandle) inode).getSlaves().toString());
            else
                formats.put("#x", "no slaves");

            formats.put("#H", inode.getParent().getName());
            formats.put("#h", inode.getParent().getPath());
        } catch (FileNotFoundException e) {
            logger.error("The file was there and now it's gone, how?", e);
        }

        String temp = _format;

        for (Map.Entry<String, String> entry : formats.entrySet()) {
            temp = temp.replaceAll(entry.getKey(), entry.getValue());
        }

        return temp;
    }

    @Override
    public boolean execInDirs() {
        return true;
    }

    @Override
    public boolean execInFiles() {
        return true;
    }

    @Override
    public boolean failed() {
        return false;
    }
}
