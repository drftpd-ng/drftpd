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
package org.drftpd.plugins.sitebot.announce.zipscript.zip;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.RankUtils;
import org.drftpd.Time;
import org.drftpd.commands.zipscript.zip.DizStatus;
import org.drftpd.commands.zipscript.zip.ZipTools;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.AnnounceInterface;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.util.UserTransferStats;
import org.drftpd.util.FileUtils;
import org.drftpd.util.GroupPosition;
import org.drftpd.util.ReplacerUtils;
import org.drftpd.util.UploaderPosition;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;
import org.tanesha.replacer.ReplacerEnvironment;

/**
 * @author djb61
 * @version $Id$
 */
public class ZipAnnouncer extends ZipTools implements AnnounceInterface {

	private static final Logger logger = Logger.getLogger(ZipAnnouncer.class);

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
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
	public void onDirectoryFtpEvent(DirectoryFtpEvent direvent) {
		if ("PRE".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "pre");
		} else if ("STOR".equals(direvent.getCommand())) {
			outputDirectorySTOR((TransferEvent) direvent);
		}
	}

	private void outputDirectorySTOR(TransferEvent fileevent) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		DirectoryHandle dir = fileevent.getDirectory();

		DizInfo dizInfo;
		ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);

		try {
			dizInfo = zipData.getDizInfo();
		} catch (FileNotFoundException ex) {
			logger.info("No diz file in " + dir.getPath() +
			", can't publish race info");
			return;
		} catch (IOException ex) {
			logger.info("Unable to read diz file in " + dir.getPath() +
			", can't publish race info");
			return;
		} catch (NoAvailableSlaveException e) {
			logger.info("No available slave for .diz");
			return;
		} catch (SlaveUnavailableException e) {
			logger.info("No available slave for .diz");
			return;
		}

		if (!fileevent.getTransferFile().getName().toLowerCase().endsWith(".zip")) {
			return;
		}

		int halfway = (int) Math.floor((double) dizInfo.getTotal() / 2);

		try {
			String username = fileevent.getUser().getName();
			DizStatus dizStatus = zipData.getDizStatus();
			
			// sanity check for when we have more zips than the .diz stated
			if (dizStatus.getMissing() < 0) {
				return;
			}

			if (dizStatus.getAvailable() == 1 && dizInfo.getTotal() > 1) {
				AnnounceWriter writer = _config.getPathWriter("store.first", fileevent.getDirectory());
				if (writer != null) {
					fillEnvSection(env, fileevent, writer, true);
					env.add("files", Integer.toString(dizInfo.getTotal()));
					env.add("expectedsize", (Bytes.formatBytes(getZipLargestFileBytes(dir) * dizInfo.getTotal())));
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.first", env, _bundle), writer);
				}
				return;
			}
			//check if new racer
			if ((dizInfo.getTotal() - dizStatus.getMissing()) != 1) {
				for (Iterator<FileHandle> iter = getZipFiles(dir).iterator(); iter.hasNext();) {
					FileHandle zipFileEntry = iter.next();

					if (!zipFileEntry.equals(fileevent.getTransferFile()) && zipFileEntry.getUsername().equals(username)
							&& zipFileEntry.getXfertime() > 0) {
						break;
					}

					if (!iter.hasNext()) {
						AnnounceWriter writer = _config.getPathWriter("store.race", fileevent.getDirectory());
						if (writer != null) {
							fillEnvSection(env, fileevent, writer, true);
							env.add("filesleft",
									Integer.toString(dizStatus.getMissing()));
							sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.race", env, _bundle), writer);
						}
					}
				}
			}

			//COMPLETE
			if (dizStatus.isFinished()) {
				AnnounceWriter writer = _config.getPathWriter("store.complete", fileevent.getDirectory());
				if (writer != null) {
					Collection<UploaderPosition> racers = RankUtils.userSort(getZipFiles(dir),
							"bytes", "high");
					Collection<GroupPosition> groups = RankUtils.topFileGroup(getZipFiles(dir));

					fillEnvSection(env, fileevent, writer, false);

					env.add("racers", Integer.toString(racers.size()));
					env.add("groups", Integer.toString(groups.size()));
					env.add("files", Integer.toString(dizInfo.getTotal()));
					env.add("size", Bytes.formatBytes(getZipTotalBytes(dir)));
					env.add("speed", Bytes.formatBytes(getXferspeed(dir)) + "/s");
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete", env, _bundle), writer);

					// Find max users/groups to announce
					int maxUsers;
					int maxGroups;
					try {
						maxUsers = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
								getPropertiesForPlugin("zipscript.conf").getProperty("irc.zip.maxusers", "10"));
					} catch (NumberFormatException e) {
						logger.error("Non numeric irc.zip.maxusers setting in zipscript.conf, using default");
						maxUsers = 10;
					}
					try {
						maxGroups = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
								getPropertiesForPlugin("zipscript.conf").getProperty("irc.zip.maxgroups", "10"));
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
							logger.warn("Error reading userfile for: "+stat.getUsername(),e2);
							continue;
						}

						ReplacerEnvironment raceenv = ReplacerEnvironment.chain(SiteBot.GLOBAL_ENV,env);

						raceenv.add("speed",
								Bytes.formatBytes(stat.getXferspeed()) + "/s");
						raceenv.add("user", stat.getUsername());
						raceenv.add("group", raceuser.getGroup());
						raceenv.add("files", "" + stat.getFiles());
						raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
						raceenv.add("position", String.valueOf(position));
						raceenv.add("percent",
								Integer.toString(
										(stat.getFiles() * 100) / dizInfo.getTotal()) + "%");
						raceenv.add("alup",
								Integer.valueOf(UserTransferStats.getStatsPlace("ALUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("monthup",
								Integer.valueOf(UserTransferStats.getStatsPlace("MONTHUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("wkup",
								Integer.valueOf(UserTransferStats.getStatsPlace("WKUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("dayup",
								Integer.valueOf(UserTransferStats.getStatsPlace("DAYUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("aldn",
								Integer.valueOf(UserTransferStats.getStatsPlace("ALDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("monthdn",
								Integer.valueOf(UserTransferStats.getStatsPlace("MONTHDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("wkdn",
								Integer.valueOf(UserTransferStats.getStatsPlace("WKDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("daydn",
								Integer.valueOf(UserTransferStats.getStatsPlace("DAYDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete.racer", raceenv, _bundle), writer);

						position++;
						if (position > maxUsers) {
							break;
						}
					}

					//add groups stats
					position = 1;

					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete.group.header", env, _bundle), writer);
					for (GroupPosition stat: groups) {
						ReplacerEnvironment raceenv = ReplacerEnvironment.chain(SiteBot.GLOBAL_ENV,env);

						raceenv.add("group", stat.getGroupname());
						raceenv.add("position", String.valueOf(position));
						raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
						raceenv.add("files", Integer.toString(stat.getFiles()));
						raceenv.add("percent",
								Integer.toString(
										(stat.getFiles() * 100) / dizInfo.getTotal()) + "%");
						raceenv.add("speed",
								Bytes.formatBytes(stat.getXferspeed()) + "/s");

						sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete.group", raceenv, _bundle), writer);

						position++;
						if (position > maxGroups) {
							break;
						}
					}
				}

				//HALFWAY
			} else if ((dizInfo.getTotal() >= 4) &&
					(dizStatus.getMissing() == halfway)) {
				AnnounceWriter writer = _config.getPathWriter("store.halfway", fileevent.getDirectory());
				if (writer != null) {
					Collection<UploaderPosition> uploaders = RankUtils.userSort(getZipFiles(dir),
							"bytes", "high");

					UploaderPosition stat = uploaders.iterator().next();

					env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
					env.add("leadfiles", Integer.toString(stat.getFiles()));
					env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
					env.add("leadpercent",
							Integer.toString((stat.getFiles() * 100) / dizInfo.getTotal()) +
					"%");
					env.add("filesleft", Integer.toString(dizStatus.getMissing()));

					User leaduser = null;
					try {
						leaduser = GlobalContext.getGlobalContext().getUserManager()
						.getUserByName(stat.getUsername());
					} catch (NoSuchUserException e3) {
						logger.warn("User not found in user database: "+stat.getUsername(),e3);
					} catch (UserFileException e3) {
						logger.warn("Error reading userfile for: "+stat.getUsername(),e3);
					}
					env.add("leaduser", leaduser != null ? leaduser.getName() : stat.getUsername());
					env.add("leadgroup", leaduser != null ? leaduser.getGroup() : "");
					fillEnvSection(env, fileevent, writer, false);
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.halfway", env, _bundle), writer);
				}
			}
		} catch (NoAvailableSlaveException e) {
			// Slave with sfv is offline
		} catch (FileNotFoundException e) {
			// SFV deleted?
		} catch (IOException e) {
			// SFV not readable
		} catch (SlaveUnavailableException e) {
			// Slave with sfv is offline
		}
	}

	private void outputDirectoryEvent(DirectoryFtpEvent direvent, String type) {
		AnnounceWriter writer = _config.getPathWriter(type, direvent.getDirectory());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			fillEnvSection(env, direvent, writer, false);
			sayOutput(ReplacerUtils.jprintf(_keyPrefix+"."+type, env, _bundle), writer);
		}
	}

	private void sayOutput(String output, AnnounceWriter writer) {
		StringTokenizer st = new StringTokenizer(output,"\n");
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			for (OutputWriter oWriter : writer.getOutputWriters()) {
				oWriter.sendMessage(token);
			}
		}
	}

	private void fillEnvSection(ReplacerEnvironment env,
			DirectoryFtpEvent direvent, AnnounceWriter writer, boolean isFile) {
		DirectoryHandle dir = direvent.getDirectory();
		env.add("user", direvent.getUser().getName());
		env.add("group", direvent.getUser().getGroup());
		env.add("section", writer.getSectionName(dir));
		env.add("path", writer.getPath(dir));

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
			env.add("size", Bytes.formatBytes(dir.getSize()));
			env.add("file", inode.getName());
			long xferSpeed = 0L;
			if (isFile) {
				FileHandle file = (FileHandle) inode;
				if (file.getXfertime() > 0) {
					xferSpeed = file.getSize() / file.getXfertime();
				}
			}
			env.add("speed",Bytes.formatBytes(xferSpeed * 1000) + "/s");
			long elapsed = event.getTime() - starttime;
			env.add("secondstocomplete",Time.formatTime(elapsed));
			long elapsedSeconds = elapsed / 1000;
			env.add("averagespeed",
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
				totalbytes += getZipTotalBytes(dir);
				totalxfertime += getZipTotalXfertime(dir);
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
						totalbytes += getZipTotalBytes(subdir);
						totalxfertime += getZipTotalXfertime(subdir);
					} catch (Exception e1) {
						// Failed to get diz data, safe to continue, that data
						// will just not be available
					}
				}
			}
			if (totaldiz > 0) {
				env.add("totalfiles", "" + totalfiles);
				env.add("totalsize",  Bytes.formatBytes(totalbytes));

				if (totalxfertime > 0) {
					env.add("totalspeed", Bytes.formatBytes((totalbytes / totalxfertime) * 1000));
				} else {
					env.add("totalspeed", Bytes.formatBytes(0));
				}
			} else {
				env.add("totalfiles", "" + 0);
				env.add("totalsize",  Bytes.formatBytes(0));
				env.add("totalspeed", Bytes.formatBytes(0));

				logger.warn("Couldn't get diz file in announce");
			}
		} catch (FileNotFoundException e1) {
			// The directory or file no longer exists, just fail out of the method
			return;
		}	
	}
}
