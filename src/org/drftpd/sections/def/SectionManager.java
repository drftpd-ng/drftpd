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
package org.drftpd.sections.def;


import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.StringTokenizer;

import org.drftpd.GlobalContext;
import org.drftpd.master.ConnectionManager;
import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.vfs.InodeHandle;


/**
 * @author mog
 * @version $Id$
 */
public class SectionManager implements SectionManagerInterface {

    public SectionManager() {
    }

    public ConnectionManager getConnectionManager() {
        return ConnectionManager.getConnectionManager();
    }

    public SectionInterface getSection(String string) {
        try {
            return new Section(getGlobalContext().getRoot().getFile(string));
        } catch (FileNotFoundException e) {
            return new Section(getGlobalContext().getRoot());
        }
    }

    private GlobalContext getGlobalContext() {
    	return GlobalContext.getGlobalContext();
	}

	public Collection<SectionInterface> getSections() {
        ArrayList<SectionInterface> sections = new ArrayList<SectionInterface>();

        for (Iterator<InodeHandle> iter = getGlobalContext().getRoot().getDirectories()
                                .iterator(); iter.hasNext();) {
        	InodeHandle dir = (InodeHandle) iter.next();
            sections.add(new Section(dir));
        }

        return sections;
    }

    public SectionInterface lookup(String string) {
        StringTokenizer st = new StringTokenizer(string, "/");

        if (!st.hasMoreTokens()) {
            return new Section(getGlobalContext().getRoot());
        }

        try {
            return new Section(getGlobalContext().getRoot().getFile(st.nextToken()));
        } catch (FileNotFoundException e) {
            return new Section(getGlobalContext().getRoot());
        }
    }

    public void reload() {
    }

    public SectionInterface lookup(InodeHandle file) {
        return lookup(file.getPath());
    }

    public class Section implements SectionInterface {
        private InodeHandle _lrf;

        public Section(InodeHandle lrf) {
            _lrf = lrf;
        }

        public InodeHandle getFile() {
            return _lrf;
        }

        public Collection getFiles() {
            return Collections.singletonList(_lrf);
        }

        public String getName() {
            return _lrf.getName();
        }

        public String getPath() {
            return _lrf.getPath();
        }

        public InodeHandle getBaseDirectory() {
            return getFile();
        }

		public String getBasePath() {
			return getPath();
		}
    }
}
