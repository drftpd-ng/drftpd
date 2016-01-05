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
package org.drftpd.commands.tvmaze;

import org.drftpd.commands.tvmaze.metadata.TvMazeInfo;
import org.drftpd.commands.tvmaze.vfs.TvMazeVFSData;
import org.drftpd.sections.SectionInterface;
import org.drftpd.vfs.DirectoryHandle;

/**
 * @author scitz0
 */
public class TvMazePrintThread extends Thread {
	private DirectoryHandle _dir;
	private SectionInterface _section;

	public TvMazePrintThread(DirectoryHandle dir, SectionInterface section) {
		setPriority(Thread.MIN_PRIORITY);
		_dir = dir;
		_section = section;
	}

	@Override
	public void run() {
		TvMazeVFSData tvmazeData = new TvMazeVFSData(_dir);
		TvMazeInfo tvmazeInfo = tvmazeData.getTvMazeInfoFromCache();
		int sleep = 0; // Time to wait for TvMaze data
		while (sleep < 10 && tvmazeInfo == null) {
			sleep++;
			try {
				sleep(1000);
			} catch (InterruptedException ie) {
				// Thread interrupted
				break;
			}
			tvmazeInfo = tvmazeData.getTvMazeInfoFromCache();
		}
		if (tvmazeInfo != null) {
			TvMazeUtils.publishEvent(tvmazeInfo, _dir, _section);
		}
	}
}