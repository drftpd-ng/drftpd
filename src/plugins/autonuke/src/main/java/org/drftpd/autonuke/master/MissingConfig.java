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
package org.drftpd.autonuke.master;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.util.Properties;

/**
 * @author scitz0
 */
public class MissingConfig extends Config {
    private static final Logger logger = LogManager.getLogger(MissingConfig.class);
    String _missing;

    public MissingConfig(int i, Properties p) {
        super(i, p);
        _missing = PropertyHelper.getProperty(p, i + ".missing");
    }

    /**
     * Boolean to return missing status
     * Minimum percent optional
     *
     * @param configData Object holding return data
     * @param dir        Directory currently being handled
     * @return Return false if dir should be nuked, else true
     */
    public boolean process(ConfigData configData, DirectoryHandle dir) {
        try {
            for (InodeHandle i : dir.getInodeHandlesUnchecked()) {
                if (i.isFile() || i.isDirectory()) {
                    if (i.getName().matches(_missing)) {
                        return true;
                    }
                }
            }
        } catch (FileNotFoundException e) {
            logger.warn("AutoNuke checkMissing: FileNotFoundException - {}", dir.getName());
            return true;
        }
        return false;
    }

}
