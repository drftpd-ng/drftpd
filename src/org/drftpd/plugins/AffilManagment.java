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
package org.drftpd.plugins;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Observable;
import java.util.Observer;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.master.config.FtpConfig;

import org.drftpd.GlobalContext;
import org.drftpd.permissions.PathPermission;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id$
 */
public class AffilManagment extends FtpListener {

	public static class AffilPermission extends PathPermission {
		private String _group;

		public AffilPermission(String group) {
			super(Collections.singletonList("=" + group));
			_group = group;
		}

		public boolean checkPath(LinkedRemoteFileInterface path) {
			List<LinkedRemoteFileInterface> files = path.getAllParentFiles();
			return (files.get(files.size() - 2).getName().equals("groups") &&
					files.get(files.size() - 3).getName().equals(_group));
		}
	}

	public void actionPerformed(Event event) {
	}

	public void unload() {
	}

	public ArrayList<String> groups;

	public void init(GlobalContext gctx) {
		super.init(gctx);
		getGlobalContext().getConfig().addObserver(
				new Observer() {
					public void update(Observable o, Object arg) {
						FtpConfig cfg = (FtpConfig) o;
						for (String group : groups) {
							cfg.addPathPermission("privpath",
									new AffilPermission(group));
						}
					}
				});
	}
}
