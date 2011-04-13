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
package org.drftpd.plugins.sitebot.announce.zipscript;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;

import org.apache.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.RankUtils;
import org.drftpd.Time;
import org.drftpd.commands.zipscript.SFVStatus;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.event.SFVMemberTransferEvent;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.protocol.zipscript.common.SFVInfo;
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
public class SFVAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = Logger.getLogger(SFVAnnouncer.class);

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
		String[] types = {"pre","store.complete","store.first","store.halfway","store.nfo","store.race"};
		return types;
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}

	@EventSubscriber
	public void onDirectoryFtpEvent(DirectoryFtpEvent dirEvent) {
		if ("PRE".equals(dirEvent.getCommand())) {
			outputDirectoryEvent(dirEvent, "pre");
		} else if ("STOR".equals(dirEvent.getCommand())) {
			outputDirectorySTOR((TransferEvent) dirEvent);
		}
	}
	
	@EventSubscriber
	public void onSFVMemberTransferEvent(SFVMemberTransferEvent sfvEvent) {
		outputSFVMemberSTOR(sfvEvent);
	}

	private void outputDirectorySTOR(TransferEvent fileevent) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		// ANNOUNCE NFO FILE
		if (fileevent.getTransferFile().getName().toLowerCase().endsWith(".nfo")) {
			AnnounceWriter writer = _config.getPathWriter("store.nfo", fileevent.getDirectory());
			if (writer != null) {
				fillEnvSection(env, fileevent, writer, true); 
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.nfo", env, _bundle), writer);
			}
		}
	}

	private void outputSFVMemberSTOR(SFVMemberTransferEvent sfvEvent) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		DirectoryHandle dir = sfvEvent.getDirectory();


		if (sfvEvent.getSFVInfo().getEntries().get(sfvEvent.getTransferFile().getName()) == null) {
			return;
		}

		int halfway = (int) Math.floor((double) sfvEvent.getSFVInfo().getSize() / 2);

		try {
			String username = sfvEvent.getUser().getName();
			SFVStatus sfvStatus = sfvEvent.getSFVStatus();

			if (sfvStatus.getAvailable() == 1 && sfvEvent.getSFVInfo().getSize() > 1) {
				AnnounceWriter writer = _config.getPathWriter("store.first", dir);
				if (writer != null) {
					fillEnvSection(env, sfvEvent, writer, true);
					env.add("files", Integer.toString(sfvEvent.getSFVInfo().getSize()));
					env.add("expectedsize", (Bytes.formatBytes(
							SFVTools.getSFVLargestFileBytes(dir, sfvEvent.getSFVData()) * sfvEvent.getSFVInfo().getSize())));
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.first", env, _bundle), writer);
				}
				return;
			}
			//check if new racer
			if ((sfvEvent.getSFVInfo().getSize() - sfvStatus.getMissing()) != 1) {
				for (Iterator<FileHandle> iter = SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()).iterator(); iter.hasNext();) {
					FileHandle sfvFileEntry = iter.next();

					if (!sfvFileEntry.equals(sfvEvent.getTransferFile()) && sfvFileEntry.getUsername().equals(username)
							&& sfvFileEntry.getXfertime() > 0) {
						break;
					}

					if (!iter.hasNext()) {
						AnnounceWriter writer = _config.getPathWriter("store.race", dir);
						if (writer != null) {
							fillEnvSection(env, sfvEvent, writer, true);
							env.add("filesleft",
									Integer.toString(sfvStatus.getMissing()));
							sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.race", env, _bundle), writer);
						}
					}
				}
			}

			//COMPLETE
			if (sfvStatus.isFinished()) {
				AnnounceWriter writer = _config.getPathWriter("store.complete", dir);
				if (writer != null) {
					Collection<UploaderPosition> racers = RankUtils.userSort(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()),
							"bytes", "high");
					Collection<GroupPosition> groups = RankUtils.topFileGroup(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()));

					fillEnvSection(env, sfvEvent, writer, false);

					env.add("racers", Integer.toString(racers.size()));
					env.add("groups", Integer.toString(groups.size()));
					env.add("files", Integer.toString(sfvEvent.getSFVInfo().getSize()));
					env.add("size", Bytes.formatBytes(SFVTools.getSFVTotalBytes(dir, sfvEvent.getSFVData())));
					env.add("speed", Bytes.formatBytes(SFVTools.getXferspeed(dir, sfvEvent.getSFVData())) + "/s");
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete", env, _bundle), writer);

					// Find max users/groups to announce
					int maxUsers;
					int maxGroups;
					try {
						maxUsers = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
								getPropertiesForPlugin("zipscript.conf").getProperty("irc.sfv.maxusers", "10"));
					} catch (NumberFormatException e) {
						logger.error("Non numeric irc.sfv.maxusers setting in zipscript.conf, using default");
						maxUsers = 10;
					}
					try {
						maxGroups = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
								getPropertiesForPlugin("zipscript.conf").getProperty("irc.sfv.maxgroups", "10"));
					} catch (NumberFormatException e) {
						logger.error("Non numeric irc.sfv.maxgroups setting in zipscript.conf, using default");
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
										(stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) + "%");
						raceenv.add("alup",
								new Integer(UserTransferStats.getStatsPlace("ALUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("monthup",
								new Integer(UserTransferStats.getStatsPlace("MONTHUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("wkup",
								new Integer(UserTransferStats.getStatsPlace("WKUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("dayup",
								new Integer(UserTransferStats.getStatsPlace("DAYUP",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("aldn",
								new Integer(UserTransferStats.getStatsPlace("ALDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("monthdn",
								new Integer(UserTransferStats.getStatsPlace("MONTHDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("wkdn",
								new Integer(UserTransferStats.getStatsPlace("WKDN",
										raceuser, GlobalContext.getGlobalContext().getUserManager())));
						raceenv.add("daydn",
								new Integer(UserTransferStats.getStatsPlace("DAYDN",
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
										(stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) + "%");
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
			} else if ((sfvEvent.getSFVInfo().getSize() >= 4) &&
					(sfvStatus.getMissing() == halfway)) {
				AnnounceWriter writer = _config.getPathWriter("store.halfway", dir);
				if (writer != null) {
					Collection<UploaderPosition> uploaders = RankUtils.userSort(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()),
							"bytes", "high");

					UploaderPosition stat = uploaders.iterator().next();

					env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
					env.add("leadfiles", Integer.toString(stat.getFiles()));
					env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
					env.add("leadpercent",
							Integer.toString((stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) +
					"%");
					env.add("filesleft", Integer.toString(sfvStatus.getMissing()));

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
					fillEnvSection(env, sfvEvent, writer, false);
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
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);

			SFVInfo sfvinfo;
			int totalsfv = 0;
			int totalfiles = 0;
			long totalbytes = 0;
			long totalxfertime = 0;

			try {
				sfvinfo = sfvData.getSFVInfo();
				totalsfv += 1;
				totalfiles += sfvinfo.getSize();
				totalbytes += SFVTools.getSFVTotalBytes(dir,sfvData);
				totalxfertime += SFVTools.getSFVTotalXfertime(dir,sfvData);
			} catch (Exception e1) {
				// Failed to get sfv data, safe to continue, that data
				// will just not be available
			}
			if (event.getCommand().equals("PRE")) {
				for (DirectoryHandle subdir : dir.getDirectoriesUnchecked()) {
					try {
						sfvData = new ZipscriptVFSDataSFV(subdir);
						sfvinfo = sfvData.getSFVInfo();
						totalsfv += 1;
						totalfiles += sfvinfo.getSize();
						totalbytes += SFVTools.getSFVTotalBytes(subdir,sfvData);
						totalxfertime += SFVTools.getSFVTotalXfertime(subdir,sfvData);
					} catch (Exception e1) {
						// Failed to get sfv data, safe to continue, that data
						// will just not be available
					}
				}
			}
			if (totalsfv > 0) {
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

				logger.warn("Couldn't get SFV file in announce");
			}
		} catch (FileNotFoundException e1) {
			// The directory or file no longer exists, just fail out of the method
			return;
		}	
	}
}
