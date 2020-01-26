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
package org.drftpd.commands.tvmaze.vfs;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.commands.tvmaze.TvMazeParser;
import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.vfs.DirectoryHandle;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * @author lh
 */
public class TvMazeVFSData {
	private static final Logger logger = LogManager.getLogger(TvMazeVFSData.class);

	private DirectoryHandle _dir;

	public TvMazeVFSData(DirectoryHandle dir) {
		_dir = dir;
	}

	public TvMazeInfo getTvMazeInfoFromCache() {
		try {
			return getTvMazeInfoFromInode(_dir);
		} catch (KeyNotFoundException e1) {
			// No TvMaze info found, return null
		} catch (FileNotFoundException e2) {
			// Hmm...
		}
		return null;
	}

	public TvMazeInfo getTvMazeInfo() throws IOException, NoAvailableSlaveException, SlaveUnavailableException {
		try {
			return getTvMazeInfoFromInode(_dir);
		} catch (KeyNotFoundException e1) {
			// bah, let's load it
		} catch (FileNotFoundException e) {
			// Directory removed, just return
			return null;
		}

		// Fetch info from TvMaze
		TvMazeParser tvmazeParser = new TvMazeParser();
		tvmazeParser.doTV(_dir.getName());

		// Did we find the show?
		if (tvmazeParser.getTvShow() == null) { return null; }

		// Add TvMaze metadata
		TvMazeInfo tvmazeInfo = tvmazeParser.getTvShow();

		try {
			_dir.addPluginMetaData(TvMazeInfo.TVMAZEINFO, tvmazeInfo);
		} catch (FileNotFoundException e) {
			logger.error("Failed to add TvMaze metadata",e);
		}

		return tvmazeInfo;
	}
	
	private TvMazeInfo getTvMazeInfoFromInode(DirectoryHandle vfsDirHandle) throws FileNotFoundException, KeyNotFoundException {
		return vfsDirHandle.getPluginMetaData(TvMazeInfo.TVMAZEINFO);
	}

}
