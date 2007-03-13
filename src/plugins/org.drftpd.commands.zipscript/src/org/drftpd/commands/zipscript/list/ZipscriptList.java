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
package org.drftpd.commands.zipscript.list;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.SFVInfo;
import org.drftpd.SFVStatus;
import org.drftpd.commands.list.AddListElementsInterface;
import org.drftpd.commands.list.ListElementsContainer;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.slave.LightRemoteInode;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.VirtualFileSystem;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipscriptList implements AddListElementsInterface {

	private static final Logger logger = Logger.getLogger(ZipscriptList.class);

	public ListElementsContainer addElements(DirectoryHandle dir, ListElementsContainer container) {

		ResourceBundle bundle = ResourceBundle.getBundle(this.getClass().getName());
		try {
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);
			SFVInfo sfvfile = sfvData.getSFVInfo();
			SFVStatus sfvstatus = sfvData.getSFVStatus();
			ReplacerEnvironment env = new ReplacerEnvironment();

			// Check config
			boolean statusBarEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("zipscript.conf").getProperty("statusbar.enabled").equals("true");
			boolean missingFilesEnabled = GlobalContext.getGlobalContext().getPluginsConfig().
				getPropertiesForPlugin("zipscript.conf").getProperty("files.missing.enabled").equals("true");
			
			if (sfvfile.getSize() != 0) {

				if (sfvstatus.getMissing() != 0) {
					env.add("missing.number","" + sfvstatus.getMissing());
					env.add("missing.percent","" + (sfvstatus.getMissing() * 100) / sfvfile.getSize());
					env.add("missing",container.getSession().jprintf(bundle,
							"statusbar.missing",env, container.getUser()));
				} else {
					env.add("missing","");
				}

				env.add("complete.total", "" + sfvfile.getSize());
				env.add("complete.number", "" + sfvstatus.getPresent());
				env.add("complete.percent", "" + (sfvstatus.getPresent() * 100)
						/ sfvfile.getSize());
				env.add("complete.totalbytes", Bytes.formatBytes(getSFVTotalBytes(dir, sfvData)));
				env.add("complete", container.getSession().jprintf(bundle,
						"statusbar.complete", env, container.getUser()));

				if (sfvstatus.getOffline() != 0) {
					env.add("offline.number","" + sfvstatus.getOffline());
					env.add("offline.percent",""+ (sfvstatus.getOffline() * 100) / sfvstatus.getPresent());
					env.add("online.number","" + sfvstatus.getPresent());
					env.add("online.percent","" + (sfvstatus.getAvailable() * 100) / sfvstatus.getPresent());
					env.add("offline",container.getSession().jprintf(bundle,
							"statusbar.offline",env,container.getUser()));
				} else {
					env.add("offline","");
				}

				/*//mp3tag info added by teflon artist, album, title, genre, year
                if (id3found) {
                	env.add("artist",mp3tag.getArtist().trim());
                	env.add("album",mp3tag.getAlbum().trim());
                	env.add("title", mp3tag.getTitle().trim());
                	env.add("genre", mp3tag.getGenre().trim());
                	env.add("year", mp3tag.getYear().trim());
                	env.add("id3tag",conn.jprintf(ListUtils.class, "statusbar.id3tag",env));
                } else {
                	env.add("id3tag","");
                }*/

				String statusDirName = null;
				statusDirName = container.getSession().jprintf(bundle,
						"statusbar.format",env, container.getUser());

				if (statusDirName == null) {
					throw new RuntimeException();
				}

				if (statusBarEnabled) {
					container.getElements().add(
							new LightRemoteInode(statusDirName, "drftpd", "drftpd", dir.lastModified(), 0L));
				}

				if (missingFilesEnabled) {
					for (String fileName : sfvfile.getEntries().keySet()) {
						FileHandle file = new FileHandle(dir.getPath()+VirtualFileSystem.separator+fileName);
						if (!file.exists()) {
							env.add("mfilename",fileName);
							container.getElements().add(new LightRemoteInode(
									container.getSession().jprintf(bundle, "files.missing.filename",env,container.getUser()),
									"drftpd", "drftpd", dir.lastModified(), 0L));
						}
					}
				}
			}
		} catch (NoAvailableSlaveException e) {
			logger.warn("No available slaves for SFV file", e);
		} catch (FileNotFoundException e) {
			// no sfv file in directory - just skip it
		}
		return container;
	}

	/* TODO The following method would be much better suited elsewhere,
	 * most likely in SFVInfo, however placing them there at this time
	 * would create dependency problems with the slave plugin.
	 * They can be moved once the sfv retrieval code in the slave is
	 * abstracted out removing the dependency of the slave code on
	 * the SFVInfo class.
	 */

	private Collection<FileHandle> getSFVFiles(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData) 
		throws FileNotFoundException, NoAvailableSlaveException {
		Collection<FileHandle> files = new ArrayList<FileHandle>();
		SFVInfo sfvInfo = sfvData.getSFVInfo();

		for (String name : sfvInfo.getEntries().keySet()) {
			FileHandle file = new FileHandle(dir.getPath()+VirtualFileSystem.separator+name);
			if (file.exists()) {
				files.add(file);
			}
		}
		return files;
	}

	private long getSFVTotalBytes(DirectoryHandle dir, ZipscriptVFSDataSFV sfvData) 
		throws FileNotFoundException, NoAvailableSlaveException {
		long totalBytes = 0;

		for (FileHandle file : getSFVFiles(dir, sfvData)) {
			if (file.getXfertime() != -1) {
				totalBytes += file.getSize();
			}
		}
		return totalBytes;
	}
}
