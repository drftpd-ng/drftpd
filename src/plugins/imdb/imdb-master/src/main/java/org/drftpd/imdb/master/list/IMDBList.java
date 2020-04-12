/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.imdb.master.list;

import org.drftpd.imdb.master.IMDBConfig;
import org.drftpd.imdb.master.vfs.IMDBVFSDataNFO;
import org.drftpd.master.commands.list.AddListElementsInterface;
import org.drftpd.master.commands.list.ListElementsContainer;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.imdb.common.IMDBInfo;
import org.drftpd.common.slave.LightRemoteInode;

import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class IMDBList implements AddListElementsInterface {

	public void initialize() { }

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		if (IMDBConfig.getInstance().barEnabled()) {
			IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO(dir);
			IMDBInfo imdbInfo = imdbData.getIMDBInfoFromCache();
			if (imdbInfo != null) {
				if (imdbInfo.getMovieFound()) {
					Map<String, Object> env = new HashMap<>();
					env.put("title", imdbInfo.getTitle());
					env.put("year", imdbInfo.getYear() != null ? imdbInfo.getYear() : "9999");
					env.put("language", imdbInfo.getLanguage());
					env.put("country", imdbInfo.getCountry());
					env.put("director", imdbInfo.getDirector());
					env.put("genres", imdbInfo.getGenres());
					env.put("plot", imdbInfo.getPlot());
					env.put("rating", imdbInfo.getRating() != null ? imdbInfo.getRating()/10+"."+imdbInfo.getRating()%10 : "0");
					env.put("votes", imdbInfo.getVotes() != null ? imdbInfo.getVotes() : "0");
					env.put("url", imdbInfo.getURL());
					env.put("runtime", imdbInfo.getRuntime() != null ? imdbInfo.getRuntime() : "0");
					String imdbDirName = container.getSession().jprintf(bundle, "imdb.dir", env, container.getUser());
					try {
						container.getElements().add(new LightRemoteInode(imdbDirName, "drftpd", "drftpd",
								IMDBConfig.getInstance().barAsDirectory(), dir.lastModified(), 0L));
					} catch (FileNotFoundException e) {
						// dir was deleted during list operation
					}
				}
			}
		}
		return container;
	}

	public void unload() {
	}

}
