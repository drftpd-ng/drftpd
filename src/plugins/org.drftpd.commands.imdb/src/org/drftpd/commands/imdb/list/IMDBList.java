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
package org.drftpd.commands.imdb.list;

import org.drftpd.commands.imdb.IMDBConfig;
import org.drftpd.commands.imdb.vfs.IMDBVFSDataNFO;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.protocol.imdb.common.IMDBInfo;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.io.FileNotFoundException;
import java.util.ResourceBundle;

/**
 * @author scitz0
 */
public class IMDBList implements AddListElementsInterface {

	public void initialize() { }

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {
		ResourceBundle bundle = container.getCommandManager().getResourceBundle();
		String keyPrefix = this.getClass().getName()+".";

		if (IMDBConfig.getInstance().barEnabled()) {
			IMDBVFSDataNFO imdbData = new IMDBVFSDataNFO(dir);
			IMDBInfo imdbInfo = imdbData.getIMDBInfoFromCache();
			if (imdbInfo != null) {
				if (imdbInfo.getMovieFound()) {
					ReplacerEnvironment env = new ReplacerEnvironment();
					env.add("title", imdbInfo.getTitle());
					env.add("year", imdbInfo.getYear() != null ? imdbInfo.getYear() : "9999");
					env.add("language", imdbInfo.getLanguage());
					env.add("country", imdbInfo.getCountry());
					env.add("director", imdbInfo.getDirector());
					env.add("genres", imdbInfo.getGenres());
					env.add("plot", imdbInfo.getPlot());
					env.add("rating", imdbInfo.getRating() != null ? imdbInfo.getRating()/10+"."+imdbInfo.getRating()%10 : "0");
					env.add("votes", imdbInfo.getVotes() != null ? imdbInfo.getVotes() : "0");
					env.add("url", imdbInfo.getURL());
					env.add("runtime", imdbInfo.getRuntime() != null ? imdbInfo.getRuntime() : "0");
					String imdbDirName = container.getSession().jprintf(bundle,
							keyPrefix+"imdb.dir", env, container.getUser());
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
