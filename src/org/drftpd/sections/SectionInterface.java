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
package org.drftpd.sections;


import java.util.Collection;

import org.drftpd.remotefile.PathReference;


/**
 * @author mog
 * @version $Id$
 */
public interface SectionInterface {
    /**
     * @return the base directory for this section.
     */
    public PathReference getBaseFile();

    /**
     * @return the (current) directory for this section.
     */
    public PathReference getFile();

    /**
     * @return all directories for this section. For example if this is a dated-dir section, it would return all dated dirs, including current dir.
     */
    public Collection getDirectories();

    /**
     * @param The file/directory to return the first subdir in this section for.
     * @return Returns the first subdirectory of the path represented that isn't the section itself.
     *         Although the returned dir can be the section itself depending on the SectionInterface implementation.
     */
    public PathReference getFirstDirInSection(
    		PathReference dir);

    /**
     * @return The name of this section
     */
    public String getName();

    /**
     * @return getFile().getPath()
     */
    public String getPath();
}
