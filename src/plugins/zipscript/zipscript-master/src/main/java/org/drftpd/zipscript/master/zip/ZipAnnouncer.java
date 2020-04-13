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
package org.drftpd.zipscript.master.zip;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.common.util.ConfigType;
import org.drftpd.master.sitebot.AbstractAnnouncer;
import org.drftpd.master.sitebot.AnnounceWriter;
import org.drftpd.master.sitebot.SiteBot;
import org.drftpd.master.sitebot.config.AnnounceConfig;
import org.drftpd.zipscript.common.zip.DizInfo;
import org.drftpd.zipscript.master.zip.event.ZipTransferEvent;
import org.drftpd.zipscript.master.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.util.RankUtils;
import org.drftpd.master.util.Time;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.event.DirectoryFtpEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.usermanager.NoSuchUserException;
import org.drftpd.master.usermanager.User;
import org.drftpd.master.usermanager.UserFileException;
import org.drftpd.master.usermanager.util.UserTransferStats;
import org.drftpd.master.util.FileUtils;
import org.drftpd.master.util.GroupPosition;
import org.drftpd.master.util.ReplacerUtils;
import org.drftpd.master.util.UploaderPosition;
import org.drftpd.master.vfs.DirectoryHandle;
import org.drftpd.master.vfs.FileHandle;
import org.drftpd.master.vfs.InodeHandle;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = LogManager.getLogger(ZipAnnouncer.class);

	private AnnounceConfig _config;

	private ResourceBundle _bundle;


	
	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;

		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		String[] types = {"pre","store.complete","store.first","store.halfway","store.race"};
		return types;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent dirEvent) {
		if ("PRE".equals(dirEvent.getCommand())) {
			outputDirectoryEvent(dirEvent, "pre");
		}
	}
	
	@EventSubscriber
	public void onZipTransferEvent(ZipTransferEvent zipEvent) {
		outputZipSTOR(zipEvent);
	}

	private void outputZipSTOR(ZipTransferEvent zipEvent) {
		Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);

		DirectoryHandle dir = zipEvent.getDirectory();

		if (!zipEvent.getTransferFile().getName().toLowerCase().endsWith(".zip")) {
			return;
		}

		int halfway = (int) Math.floor((double) zipEvent.getDizInfo().getTotal() / 2);

		try {
			String username = zipEvent.getUser().getName();
			
			// sanity check for when we have more zips than the .diz stated
			if (zipEvent.getDizStatus().getMissing() < 0) {
				return;
			}

			if (zipEvent.getDizStatus().getAvailable() == 1 && zipEvent.getDizInfo().getTotal() > 1) {
				AnnounceWriter writer = _config.getPathWriter("store.first", dir);
				if (writer != null) {
					fillEnvSection(env, zipEvent, writer, true);
					env.put("files", Integer.toString(zipEvent.getDizInfo().getTotal()));
					env.put("expectedsize", (Bytes.formatBytes(
							ZipTools.getZipLargestFileBytes(dir) * zipEvent.getDizInfo().getTotal())));
					sayOutput(ReplacerUtils.jprintf("zip.store.first", env, _bundle), writer);
				}
				return;
			}
			//check if new racer
			if ((zipEvent.getDizInfo().getTotal() - zipEvent.getDizStatus().getMissing()) != 1) {
				for (Iterator<FileHandle> iter = ZipTools.getZipFiles(dir).iterator(); iter.hasNext();) {
					FileHandle zipFileEntry = iter.next();

					if (!zipFileEntry.equals(zipEvent.getTransferFile()) && zipFileEntry.getUsername().equals(username)
							&& zipFileEntry.getXfertime() > 0) {
						break;
					}

					if (!iter.hasNext()) {
						AnnounceWriter writer = _config.getPathWriter("store.race", dir);
						if (writer != null) {
							fillEnvSection(env, zipEvent, writer, true);
							env.put("filesleft",
									Integer.toString(zipEvent.getDizStatus().getMissing()));
							sayOutput(ReplacerUtils.jprintf("zip.store.race", env, _bundle), writer);
						}
					}
				}
			}

			//COMPLETE
			if (zipEvent.getDizStatus().isFinished()) {
				AnnounceWriter writer = _config.getPathWriter("store.complete", dir);
				if (writer != null) {
					Collection<UploaderPosition> racers = RankUtils.userSort(ZipTools.getZipFiles(dir),
							"bytes", "high");
					Collection<GroupPosition> groups = RankUtils.topFileGroup(ZipTools.getZipFiles(dir));

					fillEnvSection(env, zipEvent, writer, false);

					env.put("racers", Integer.toString(racers.size()));
					env.put("groups", Integer.toString(groups.size()));
					env.put("files", Integer.toString(zipEvent.getDizInfo().getTotal()));
					env.put("size", Bytes.formatBytes(ZipTools.getZipTotalBytes(dir)));
					env.put("speed", Bytes.formatBytes(ZipTools.getXferspeed(dir)) + "/s");
					sayOutput(ReplacerUtils.jprintf("zip.store.complete", env, _bundle), writer);

					// Find max users/groups to announce
					int maxUsers;
					int maxGroups;
					Properties cfg = ConfigLoader.loadPluginConfig("zipscript.conf", ConfigType.MASTER);
					try {

						maxUsers = Integer.parseInt(cfg.getProperty("irc.zip.maxusers", "10"));
					} catch (NumberFormatException e) {
						logger.error("Non numeric irc.zip.maxusers setting in zipscript.conf, using default");
						maxUsers = 10;
					}
					try {
						maxGroups = Integer.parseInt(cfg.getProperty("irc.zip.maxgroups", "10"));
					} catch (NumberFormatException e) {
						logger.error("Non numeric irc.zip.maxgroups setting in zipscript.conf, using default");
						maxGroups = 10;
					}

					// Add racer stats
					int position = 1;

					for (UploaderPosition stat : racers) {
						User raceuser;

						try {
							raceuser = GlobalContext.getGlobalContext().getUserManager()
							.getUserByName(stat.getUsername());
						} catch (NoSuchUserException e2) {
							continue;
						} catch (UserFileException e2) {
                            logger.warn("Error reading userfile for: {}", stat.getUsername(), e2);
							continue;
						}

						Map<String, Object> raceenv = new HashMap<>(env);

						raceenv.put("speed",
								Bytes.formatBytes(stat.getXferspeed()) + "/s");
						raceenv.put("user", stat.getUsername());
						raceenv.put("group", raceuser.getGroup());
						raceenv.put("files", "" + stat.getFiles());
						raceenv.put("size", Bytes.formatBytes(stat.getBytes()));
						raceenv.put("position", String.valueOf(position));
						raceenv.put("percent",
								Integer.toString(
										(stat.getFiles() * 100) / zipEvent.getDizInfo().getTotal()) + "%");
						raceenv.put("alup",
                                UserTransferStats.getStatsPlace("ALUP",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("monthup",
                                UserTransferStats.getStatsPlace("MONTHUP",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("wkup",
                                UserTransferStats.getStatsPlace("WKUP",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("dayup",
                                UserTransferStats.getStatsPlace("DAYUP",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("aldn",
                                UserTransferStats.getStatsPlace("ALDN",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("monthdn",
                                UserTransferStats.getStatsPlace("MONTHDN",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("wkdn",
                                UserTransferStats.getStatsPlace("WKDN",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						raceenv.put("daydn",
                                UserTransferStats.getStatsPlace("DAYDN",
                                        raceuser, GlobalContext.getGlobalContext().getUserManager()));
						sayOutput(ReplacerUtils.jprintf("zip.store.complete.racer", raceenv, _bundle), writer);

						position++;
						if (position > maxUsers) {
							break;
						}
					}

					//add groups stats
					position = 1;

					sayOutput(ReplacerUtils.jprintf("zip.store.complete.group.header", env, _bundle), writer);
					for (GroupPosition stat: groups) {
						Map<String, Object> raceenv = new HashMap<>(env);

						raceenv.put("group", stat.getGroupname());
						raceenv.put("position", String.valueOf(position));
						raceenv.put("size", Bytes.formatBytes(stat.getBytes()));
						raceenv.put("files", Integer.toString(stat.getFiles()));
						raceenv.put("percent",
								Integer.toString(
										(stat.getFiles() * 100) / zipEvent.getDizInfo().getTotal()) + "%");
						raceenv.put("speed",
								Bytes.formatBytes(stat.getXferspeed()) + "/s");

						sayOutput(ReplacerUtils.jprintf("zip.store.complete.group", raceenv, _bundle), writer);

						position++;
						if (position > maxGroups) {
							break;
						}
					}
				}

				//HALFWAY
			} else if ((zipEvent.getDizInfo().getTotal() >= 4) &&
					(zipEvent.getDizStatus().getMissing() == halfway)) {
				AnnounceWriter writer = _config.getPathWriter("store.halfway", dir);
				if (writer != null) {
					Collection<UploaderPosition> uploaders = RankUtils.userSort(ZipTools.getZipFiles(dir),
							"bytes", "high");

					UploaderPosition stat = uploaders.iterator().next();

					env.put("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
					env.put("leadfiles", Integer.toString(stat.getFiles()));
					env.put("leadsize", Bytes.formatBytes(stat.getBytes()));
					env.put("leadpercent",
							Integer.toString((stat.getFiles() * 100) / zipEvent.getDizInfo().getTotal()) +
					"%");
					env.put("filesleft", Integer.toString(zipEvent.getDizStatus().getMissing()));

					User leaduser = null;
					try {
						leaduser = GlobalContext.getGlobalContext().getUserManager()
						.getUserByName(stat.getUsername());
					} catch (NoSuchUserException e3) {
                        logger.warn("User not found in user database: {}", stat.getUsername(), e3);
					} catch (UserFileException e3) {
                        logger.warn("Error reading userfile for: {}", stat.getUsername(), e3);
					}
					env.put("leaduser", leaduser != null ? leaduser.getName() : stat.getUsername());
					env.put("leadgroup", leaduser != null ? leaduser.getGroup() : "");
					fillEnvSection(env, zipEvent, writer, false);
					sayOutput(ReplacerUtils.jprintf("zip.store.halfway", env, _bundle), writer);
				}
			}
		} catch (FileNotFoundException e) {
			// SFV deleted?
		} catch (IOException e) {
			// SFV not readable
		}
	}

	private void outputDirectoryEvent(DirectoryFtpEvent direvent, String type) {
		AnnounceWriter writer = _config.getPathWriter(type, direvent.getDirectory());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);
			fillEnvSection(env, direvent, writer, false);
			sayOutput(ReplacerUtils.jprintf(type, env, _bundle), writer);
		}
	}

	private void fillEnvSection(Map<String, Object> env,
			DirectoryFtpEvent direvent, AnnounceWriter writer, boolean isFile) {
		DirectoryHandle dir = direvent.getDirectory();
		env.put("user", direvent.getUser().getName());
		env.put("group", direvent.getUser().getGroup());
		env.put("section", writer.getSectionName(dir));
		env.put("sectioncolor", GlobalContext.getGlobalContext().getSectionManager().lookup(dir).getColor());
		env.put("path", writer.getPath(dir));

		TransferEvent event = null;

		if (direvent instanceof TransferEvent) {
			event = (TransferEvent) direvent;
		} else {
			return;
		}
		InodeHandle inode;
		if (isFile) {
			inode = event.getTransferFile();
		} else {
			inode = event.getDirectory();
		}
		long starttime;
		try {
			try {
				FileHandle oldestFile = FileUtils.getOldestFile(dir);
				starttime = oldestFile.lastModified();
			} catch (NoSuchElementException e) {
				starttime = dir.lastModified();
			}
			env.put("size", Bytes.formatBytes(dir.getSize()));
			env.put("file", inode.getName());
			long xferSpeed = 0L;
			if (isFile) {
				FileHandle file = (FileHandle) inode;
				if (file.getXfertime() > 0) {
					xferSpeed = file.getSize() / file.getXfertime();
				}
			}
			env.put("speed",Bytes.formatBytes(xferSpeed * 1000) + "/s");
			long elapsed = event.getTime() - starttime;
			env.put("secondstocomplete", Time.formatTime(elapsed));
			long elapsedSeconds = elapsed / 1000;
			env.put("averagespeed",
					(elapsedSeconds == 0) ? "n/a"
							: (Bytes.formatBytes(
									inode.getSize() / elapsedSeconds) + "/s"));
			ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);

			DizInfo dizInfo;
			int totaldiz = 0;
			int totalfiles = 0;
			long totalbytes = 0;
			long totalxfertime = 0;

			try {
				dizInfo = zipData.getDizInfo();
				totaldiz += 1;
				totalfiles += dizInfo.getTotal();
				totalbytes += ZipTools.getZipTotalBytes(dir);
				totalxfertime += ZipTools.getZipTotalXfertime(dir);
			} catch (Exception e1) {
				// Failed to get diz data, safe to continue, that data
				// will just not be available
			}
			if (event.getCommand().equals("PRE")) {
				for (DirectoryHandle subdir : dir.getDirectoriesUnchecked()) {
					try {
						zipData = new ZipscriptVFSDataZip(subdir);
						dizInfo = zipData.getDizInfo();
						totaldiz += 1;
						totalfiles += dizInfo.getTotal();
						totalbytes += ZipTools.getZipTotalBytes(subdir);
						totalxfertime += ZipTools.getZipTotalXfertime(subdir);
					} catch (Exception e1) {
						// Failed to get diz data, safe to continue, that data
						// will just not be available
					}
				}
			}
			if (totaldiz > 0) {
				env.put("totalfiles", "" + totalfiles);
				env.put("totalsize",  Bytes.formatBytes(totalbytes));

				if (totalxfertime > 0) {
					env.put("totalspeed", Bytes.formatBytes((totalbytes / totalxfertime) * 1000));
				} else {
					env.put("totalspeed", Bytes.formatBytes(0));
				}
			} else {
				env.put("totalfiles", "" + 0);
				env.put("totalsize",  Bytes.formatBytes(0));
				env.put("totalspeed", Bytes.formatBytes(0));

				logger.warn("Couldn't get diz file in announce");
			}
		} catch (FileNotFoundException e1) {
			// The directory or file no longer exists, just fail out of the method
        }
	}
}
