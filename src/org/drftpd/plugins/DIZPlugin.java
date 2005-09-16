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

import java.io.FileNotFoundException;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;

import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.plugins.SiteBot.Ret;
import org.drftpd.remotefile.LinkedRemoteFileInterface;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.SimplePrintf;

public class DIZPlugin extends FtpListener {
	private static final String _ehdr = "DIZFile exception:";

	private static final Logger logger = Logger.getLogger(DIZPlugin.class);

	public DIZPlugin() {
		logger.info("DIZPlugin loaded successfully");
	}

	public void actionPerformed(Event event) {
		// If this transfer event is STOR
		if (event.getCommand().equals("STOR")) {
			actionPerformedSTOR((TransferEvent) event);
		}

	}

	public synchronized void actionPerformedSTOR(TransferEvent xferEvent) {
		// Is this a zip file?
		if (!isZipFile(xferEvent.getDirectory())) {
			return;
		}

		// Are we logging this directory for this user?
		if (!GlobalContext.getGlobalContext().getConfig().checkPathPermission("dirlog",
				xferEvent.getUser(), xferEvent.getDirectory())) {
			return;
		}
		DIZFile diz = null;
		try {
			diz = new DIZFile(xferEvent.getDirectory());
		} catch (FileNotFoundException e) {
			return;
		} catch (NoAvailableSlaveException e) {
			return;
		}

		if (zipFilesPresent(diz.getParent()) == diz.getTotal()) {
			if ((diz.getTotal() - DIZPlugin.zipFilesPresent(diz.getParent())) <= 0) {
				ReplacerEnvironment env = new ReplacerEnvironment(
						SiteBot.GLOBAL_ENV);

				try {
					Ret ret = getSiteBot().getPropertyFileSuffix(
							"store.complete.diz", diz.getParent());

					Collection racers = SiteBot.userSort(diz.getParent()
							.getFiles(), "bytes", "high");

					Collection groups = SiteBot.topFileGroup(diz.getParent()
							.getFiles());

					env.add("section", "" + ret.getSection().getName());
					env.add("path", "" + diz.getParent().getName());
					env.add("size", ""
							+ Bytes.formatBytes(zipDirSize(diz.getParent())));
					env.add("files", "" + diz.getTotal());
					env.add("speed", ""
							+ (zipDirSize(diz.getParent()) / zipDirTime(diz
									.getParent())) + "KB/s");
					env.add("time", ""
							+ Time.formatTime(zipDirTime(diz.getParent())));
					env.add("racers", "" + racers.size());
					env.add("groups", "" + groups.size());

					getSiteBot().say(ret.getSection(),
							SimplePrintf.jprintf(ret.getFormat(), env));
				} catch (FormatterException e) {
					logger.warn("", e);
				} catch (ObjectNotFoundException e) {
					return; // sitebot not loaded
				}
			}
		}

		return;
	}

	public SiteBot getSiteBot() throws ObjectNotFoundException {
		return (SiteBot) GlobalContext.getGlobalContext().getFtpListener(SiteBot.class);
	}

	public static boolean isZipFile(LinkedRemoteFileInterface file) {
		return file.getName().toLowerCase().endsWith(".zip") && file.isFile();
	}

	public static long zipDirSize(LinkedRemoteFileInterface dir) {

		long size = 0;
		for (LinkedRemoteFileInterface aFile : dir.getFiles2()) {
			if (isZipFile(aFile)) {
				size += aFile.length();
			}
		}
		return size;
	}

	public static long zipDirTime(LinkedRemoteFileInterface dir) {
		long time = 0;
		for (LinkedRemoteFileInterface aFile : dir.getFiles2()) {
			if (isZipFile(aFile)) {
				time += aFile.getXfertime();
			}
		}
		return time;
	}

	public static int zipFilesPresent(LinkedRemoteFileInterface dir) {
		int total = 0;
		for (LinkedRemoteFileInterface aFile : dir.getFiles2()) {
			if (isZipFile(aFile) && aFile.length() != 0) {
				total++;
			}
		}
		return total;
	}

	public static int zipFilesOnline(LinkedRemoteFileInterface dir) {
		int total = 0;
		for (LinkedRemoteFileInterface aFile : dir.getFiles2()) {
			if (isZipFile(aFile) && aFile.length() != 0 && aFile.isAvailable()) {
				total++;
			}
		}
		return total;
	}

	public static int zipFilesOffline(LinkedRemoteFileInterface dir) {
		return zipFilesPresent(dir) - zipFilesOnline(dir);
	}

	public static LinkedRemoteFileInterface getZipFile(
			LinkedRemoteFileInterface dir) throws NoAvailableSlaveException {
		for (LinkedRemoteFileInterface aFile : dir.getFiles2()) {
			if (isZipFile(aFile) && aFile.length() != 0 && aFile.isAvailable()) {
				return aFile;
			}
				
		}
		throw new NoAvailableSlaveException();
	}
}
