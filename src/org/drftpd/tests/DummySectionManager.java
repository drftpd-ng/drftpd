/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
package org.drftpd.tests;


import org.drftpd.master.ConnectionManager;
import org.drftpd.remotefile.FileUtils;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;

import java.io.FileNotFoundException;

import java.util.Collection;


public class DummySectionManager implements SectionManagerInterface {
    private LinkedRemoteFileInterface _sectionDir;
    private SectionInterface _section = new SectionInterface() {
            public LinkedRemoteFileInterface getFile() {
                return _sectionDir;
            }

            public Collection getFiles() {
                throw new UnsupportedOperationException();
            }

            public String getName() {
                throw new UnsupportedOperationException();
            }

            public String getPath() {
                return getFile().getPath();
            }

            public LinkedRemoteFileInterface getFirstDirInSection(
                LinkedRemoteFileInterface dir) {
                //							LinkedRemoteFileInterface dir1 = dir, dir2 = dir;
                //							while(dir1 != getFile()) {
                //								dir2 = dir1;
                //								try {
                //									dir1 = dir1.getParentFile();
                //								} catch (FileNotFoundException e) {
                //									return getFile();
                //								}
                //							}
                //							return dir2;
                try {
                    return FileUtils.getSubdirOfDirectory(getFile(), dir);
                } catch (FileNotFoundException e) {
                    return dir;
                }
            }

            public LinkedRemoteFileInterface getBaseFile() {
                return getFile();
            }

			public String getBasePath() {
				return getPath();
			}
        };

    public DummySectionManager(LinkedRemoteFileInterface sectionDir) {
        _sectionDir = sectionDir;
    }

    public SectionInterface getSection(String string) {
        return _section;
    }

    public void reload() {
    }

    public ConnectionManager getConnectionManager() {
        throw new UnsupportedOperationException();
    }

    public Collection getSections() {
        throw new UnsupportedOperationException();
    }

    public SectionInterface lookup(String string) {
        return _section;
    }

    public SectionInterface lookup(LinkedRemoteFileInterface file) {
        return _section;
    }
}
