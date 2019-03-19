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
package org.drftpd.commands.find.imdb;

import org.apache.commons.text.WordUtils;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commands.find.action.ActionInterface;
import org.drftpd.commands.imdb.vfs.IMDBVFSDataNFO;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.InodeHandle;

/**
 * @author scitz0
 * @version $Id: NukeAction.java 2482 2011-06-28 10:20:44Z scitz0 $
 */
public class IMDBAction implements ActionInterface {
	private boolean _failed;

	@Override
	public void initialize(String action, String[] args) throws ImproperUsageException {
	}

	@Override
	public String exec(CommandRequest request, InodeHandle inode) {
		_failed = false;
		IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO((DirectoryHandle)inode);
		IMDBInfo imdbInfo = imdbData.getIMDBInfoFromCache();
		if (imdbInfo != null) {
			if (imdbInfo.getMovieFound()) {
				StringBuilder sb = new StringBuilder();
				sb.append("#########################################").append(")\n");
				sb.append("# Title # - ").append(imdbInfo.getTitle()).append("\n");
				sb.append("# Year # - ").append(imdbInfo.getYear()).append("\n");
				sb.append("# Runtime # - ").append(imdbInfo.getRuntime()).append(" min").append("\n");
				sb.append("# Language # - ").append(imdbInfo.getLanguage()).append("\n");
				sb.append("# Country # - ").append(imdbInfo.getCountry()).append("\n");
				sb.append("# Director # - ").append(imdbInfo.getDirector()).append("\n");
				sb.append("# Genres # - ").append(imdbInfo.getGenres()).append("\n");
				sb.append("# Plot #\n").append(WordUtils.wrap(imdbInfo.getPlot(), 70));
				sb.append("# Rating # - ");
				sb.append(imdbInfo.getRating() != null ? imdbInfo.getRating()/10+"."+imdbInfo.getRating()%10+"/10" : "-").append("\n");
				sb.append("# Votes # - ").append(imdbInfo.getVotes()).append("\n");
				sb.append("# URL # - ").append(imdbInfo.getURL()).append("\n");
				return sb.toString();
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
