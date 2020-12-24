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
package org.drftpd.imdb.master.find;

import org.apache.commons.text.WordUtils;
import org.drftpd.find.master.action.ActionInterface;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.imdb.master.vfs.IMDBVFSDataNFO;
import org.drftpd.master.commands.CommandRequest;
import org.drftpd.master.commands.ImproperUsageException;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.InodeHandle;

/**
 * @author scitz0
 * @version $Id: NukeAction.java 2482 2011-06-28 10:20:44Z scitz0 $
 */
public class IMDBAction implements ActionInterface {
    private boolean _failed;

    @Override
    public String name() {
        return "PrintIMDB";
    }

    @Override
    public void initialize(String action, String[] args) throws ImproperUsageException {
    }

    @Override
    public String exec(CommandRequest request, InodeHandle inode) {
        _failed = false;
        IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO((DirectoryHandle) inode);
        IMDBInfo imdbInfo = imdbData.getIMDBInfoFromCache();
        if (imdbInfo != null) {
            if (imdbInfo.getMovieFound()) {
                String sb = "#########################################" + ")\n" +
                        "# Title # - " + imdbInfo.getTitle() + "\n" +
                        "# Year # - " + imdbInfo.getYear() + "\n" +
                        "# Runtime # - " + imdbInfo.getRuntime() + " min" + "\n" +
                        "# Language # - " + imdbInfo.getLanguage() + "\n" +
                        "# Country # - " + imdbInfo.getCountry() + "\n" +
                        "# Director # - " + imdbInfo.getDirector() + "\n" +
                        "# Genres # - " + imdbInfo.getGenres() + "\n" +
                        "# Plot #\n" + WordUtils.wrap(imdbInfo.getPlot(), 70) +
                        "# Rating # - " +
                        (imdbInfo.getRating() != null ? imdbInfo.getRating() / 10 + "." + imdbInfo.getRating() % 10 + "/10" : "-") + "\n" +
                        "# Votes # - " + imdbInfo.getVotes() + "\n" +
                        "# URL # - " + imdbInfo.getURL() + "\n";
                return sb;
            }
        }
        return "#########################################\nNO IMDB INFO FOUND FOR: " + inode.getPath();
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
