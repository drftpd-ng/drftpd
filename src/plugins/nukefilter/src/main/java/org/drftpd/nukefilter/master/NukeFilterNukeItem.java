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
package org.drftpd.nukefilter.master;

import org.drftpd.master.GlobalContext;
import org.drftpd.master.sections.SectionInterface;
import org.drftpd.master.vfs.DirectoryHandle;

/**
 * @author phew
 */
public class NukeFilterNukeItem {

    private final DirectoryHandle dir;
    private final String reason;
    private final String element;
    private final int delay;
    private final int nukex;

    public NukeFilterNukeItem(DirectoryHandle dir, String reason, String element, int delay, int nukex) {
        this.dir = dir;
        this.reason = reason;
        this.element = element;
        this.delay = delay;
        this.nukex = nukex;
    }

    public DirectoryHandle getDirectoryHandle() {
        return dir;
    }

    public String getDirectoryName() {
        return dir.getName();
    }

    public String getPath() {
        return dir.getParent().getPath();
    }

    public String getSectionName() {
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
        return section.getName();
    }

    public String getSectionColor() {
        SectionInterface section = GlobalContext.getGlobalContext().getSectionManager().lookup(dir);
        return section.getColor();
    }

    public String getReason() {
        return reason;
    }

    public String getElement() {
        return element;
    }

    public int getDelay() {
        return delay;
    }

    public int getNukex() {
        return nukex;
    }

}
