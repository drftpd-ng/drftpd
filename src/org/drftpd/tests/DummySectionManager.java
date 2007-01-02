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


import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Set;

import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.master.ConnectionManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.vfs.DirectoryHandle;


public class DummySectionManager implements SectionManagerInterface {
    private DirectoryHandle _sectionDir;
    private static final Logger logger = Logger.getLogger(DummySectionManager.class);
    private SectionInterface _section = new SectionInterface() {
            public DirectoryHandle getDirectory() {
                return _sectionDir;
            }

            public Collection getFiles() {
                throw new UnsupportedOperationException();
            }

            public String getName() {
                throw new UnsupportedOperationException();
            }

            public String getPath() {
                return getDirectory().getPath();
            }

            public DirectoryHandle getFirstDirInSection(
            		DirectoryHandle dir) {
            	try {
					for (DirectoryHandle first : dir.getDirectories()) {
						return first;
					}
				} catch (FileNotFoundException e) {
					logger.debug(e);
				}
            	// bah, just don't feel like dealing with it -zubov
            	return null;
            }

            public DirectoryHandle getBaseFile() {
                return getDirectory();
            }

			public String getBasePath() {
				return getPath();
			}

			public DirectoryHandle getBaseDirectory() {
				// TODO Auto-generated method stub
				return null;
			}

			public DirectoryHandle getCurrentDirectory() {
				// TODO Auto-generated method stub
				return null;
			}

			public Set<DirectoryHandle> getDirectories() {
				// TODO Auto-generated method stub
				return null;
			}
        };

    public DummySectionManager(DirectoryHandle sectionDir) {
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

    public SectionInterface lookup(DirectoryHandle file) {
        return _section;
    }
}
