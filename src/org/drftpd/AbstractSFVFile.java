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
package org.drftpd;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author mog
 * @version $Id: AbstractSFVFile.java,v 1.1 2004/11/09 18:59:53 mog Exp $
 */
public class AbstractSFVFile {
    /**
     * String fileName as key.
     * Long checkSum as value.
     */
    protected HashMap _entries = new HashMap();

    /**
     * Returns a map having <code>String filename</code> as key and <code>Long checksum</code> as value.
     * @return a map having <code>String filename</code> as key and <code>Long checksum</code> as value.
     */
    public Map getEntries() {
        return _entries;
    }

    /**
     * Returns the names of the files in this .sfv file
     */
    public Collection getNames() {
        return getEntries().keySet();
    }

    public boolean hasFile(String name) {
        return getEntries().containsKey(name);
    }
    /**
     * @return Number of file entries in the .sfv
     */
    public int size() {
        return _entries.size();
    }
}
