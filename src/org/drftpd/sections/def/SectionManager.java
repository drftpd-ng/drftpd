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

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.remotefile.FileUtils;

import org.drftpd.sections.SectionInterface;
import org.drftpd.sections.SectionManagerInterface;

import java.io.FileNotFoundException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.StringTokenizer;


/**
 * @author mog
 * @version $Id: SectionManager.java,v 1.9 2004/09/25 03:48:41 mog Exp $
 */
public class SectionManager implements SectionManagerInterface {
    private ConnectionManager _cm;

    public SectionManager(ConnectionManager cm) {
        _cm = cm;
    }

    public ConnectionManager getConnectionManager() {
        return _cm;
    }

    public SectionInterface getSection(String string) {
        try {
            return new Section(_cm.getGlobalContext().getRoot().getFile(string));
        } catch (FileNotFoundException e) {
            return new Section(_cm.getGlobalContext().getRoot());
        }
    }

    public Collection getSections() {
        ArrayList sections = new ArrayList();

        for (Iterator iter = _cm.getGlobalContext().getRoot().getDirectories()
                                .iterator(); iter.hasNext();) {
            LinkedRemoteFileInterface dir = (LinkedRemoteFileInterface) iter.next();
            sections.add(new Section(dir));
        }

        return sections;
    }

    public SectionInterface lookup(String string) {
        StringTokenizer st = new StringTokenizer(string, "/");

        try {
            return new Section(_cm.getGlobalContext().getRoot().getFile(st.nextToken()));
        } catch (FileNotFoundException e) {
            return new Section(_cm.getGlobalContext().getRoot());
        }
    }

    public void reload() {
    }

    public SectionInterface lookup(LinkedRemoteFileInterface file) {
        return lookup(file.getPath());
    }

    public class Section implements SectionInterface {
        private LinkedRemoteFileInterface _lrf;

        public Section(LinkedRemoteFileInterface lrf) {
            _lrf = lrf;
        }

        public LinkedRemoteFileInterface getFile() {
            return _lrf;
        }

        public Collection getFiles() {
            return Collections.singletonList(_lrf);
        }

        public LinkedRemoteFileInterface getFirstDirInSection(
            LinkedRemoteFileInterface dir) {
            try {
                return FileUtils.getSubdirOfDirectory(getFile(), dir);
            } catch (FileNotFoundException e) {
                return dir;
            }
        }

        public String getName() {
            return _lrf.getName();
        }

        public String getPath() {
            return _lrf.getPath();
        }
    }
}
