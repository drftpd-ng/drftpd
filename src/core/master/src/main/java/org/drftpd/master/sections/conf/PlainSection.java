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
package org.drftpd.master.sections.conf;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.util.PropertyHelper;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.VirtualFileSystem;
import org.drftpd.slave.exceptions.FileExistsException;

import java.io.FileNotFoundException;
import java.util.Collections;
import java.util.Properties;
import java.util.Set;

/**
 * @author mog
 * @version $Id$
 */
public class PlainSection implements ConfigurableSectionInterface {
    private static final Logger logger = LogManager.getLogger(PlainSection.class);
    protected DirectoryHandle _basePath;
    private final String _name;
    private final String _color;

    public PlainSection(int i, Properties p) {
        this(PropertyHelper.getProperty(p, i + ".name"), PropertyHelper.getProperty(p, i + ".color", "15"),
                new DirectoryHandle(PropertyHelper.getProperty(p, i + ".path")));
    }

    public PlainSection(String name, DirectoryHandle dir) {
        _name = name;
        _color = "15";
        _basePath = dir;
    }

    public PlainSection(String name, String color, DirectoryHandle dir) {
        _name = name;
        _color = color;
        _basePath = dir;
    }

    protected static GlobalContext getGlobalContext() {
        return GlobalContext.getGlobalContext();
    }

    public String getName() {
        return _name;
    }

    public String getColor() {
        return _color;
    }

    public DirectoryHandle getBaseDirectory() {
        return _basePath;
    }

    public String getBasePath() {
        return _basePath.getPath();
    }

    public DirectoryHandle getCurrentDirectory() {
        return getBaseDirectory();
    }

    @SuppressWarnings("unchecked")
    public Set<DirectoryHandle> getDirectories() {
        try {
            return getBaseDirectory().getDirectoriesUnchecked();
        } catch (FileNotFoundException e) {
            return Collections.emptySet();
        }
    }

    public void createSectionDir() {
        try {
            String path = getCurrentDirectory().getPath();
            DirectoryHandle dir = new DirectoryHandle(VirtualFileSystem.stripLast(path));
            dir.createDirectoryRecursive(VirtualFileSystem.getLast(path), true);
        } catch (FileExistsException e) {
            // good the file exists, no need to create it.
        } catch (FileNotFoundException e) {
            logger.error("What happened? I don't know how to handle this", e);
        }
    }
}
