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
package org.drftpd.tvmaze.master.find;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;
import org.drftpd.find.master.action.ActionInterface;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;
import org.drftpd.tvmaze.master.metadata.TvMazeInfo;
import org.drftpd.tvmaze.master.vfs.TvMazeVFSData;

import java.util.Arrays;

/**
 * @author scitz0
 * @version $Id: NukeAction.java 2482 2011-06-28 10:20:44Z scitz0 $
 */
public class TvMazeAction implements ActionInterface {
    private boolean _failed;

    @Override
    public String name() {
        return "PrintTvMaze";
    }

    @Override
    public void initialize(String action, String[] args) throws ImproperUsageException {
    }

    @Override
    public String exec(CommandRequest request, InodeHandle inode) {
        _failed = false;
        TvMazeVFSData tvmazeData = new TvMazeVFSData((DirectoryHandle) inode);
        TvMazeInfo tvmazeInfo = tvmazeData.getTvMazeInfoFromCache();
        if (tvmazeInfo != null) {
            String sb = "#########################################" + ")\n" +
                    "# Title # - " + tvmazeInfo.getName() + "\n" +
                    "# Genre # - " + StringUtils.join(Arrays.toString(tvmazeInfo.getGenres()), ", ") + "\n" +
                    "# URL # - " + tvmazeInfo.getURL() + "\n" +
                    "# Plot #\n" + WordUtils.wrap(tvmazeInfo.getSummary(), 70);
            return sb;
        }
        return "#########################################\nNO TvMaze INFO FOUND FOR: " + inode.getPath();
    }

    @Override
    public boolean execInDirs() {
        return true;
    }

    @Override
    public boolean execInFiles() {
        return false;
    }

    @Override
    public boolean failed() {
        return _failed;
    }
}
