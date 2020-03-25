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
import java.util.*;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.event.SFVMemberTransferEvent;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.RankUtils;
import org.drftpd.master.Time;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.common.dynamicdata.Key;
import org.drftpd.master.common.dynamicdata.KeyNotFoundException;
import org.drftpd.master.event.DirectoryFtpEvent;
import org.drftpd.master.event.TransferEvent;
import org.drftpd.master.exceptions.NoAvailableSlaveException;
import org.drftpd.master.exceptions.SlaveUnavailableException;
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
import org.drftpd.plugins.sitebot.AbstractAnnouncer;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;

/**
 * @author djb61
 * @version $Id$
 */
public class SFVAnnouncer extends AbstractAnnouncer {

	private static final Logger logger = LogManager.getLogger(SFVAnnouncer.class);

	public static final Key<Boolean> SFV_FIRST = new Key<>(SFVAnnouncer.class, "sfv_first");

	public static final Key<Boolean> SFV_HALFWAY = new Key<>(SFVAnnouncer.class, "sfv_halfway");

	public static final Key<Boolean> SFV_COMPLETE = new Key<>(SFVAnnouncer.class, "sfv_complete");

	private Timer _timer;

	private AnnounceConfig _config;

	private ResourceBundle _bundle;


	
	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_timer = new Timer();
		_config = config;
		_bundle = bundle;

		// Subscribe to events
		AnnotationProcessor.process(this);
	}

	public void stop() {
		_timer.cancel();
		AnnotationProcessor.unprocess(this);
	}

	public String[] getEventTypes() {
		return new String[]{"pre","store.complete","store.first","store.halfway","store.race"};
	}
	
	public void setResourceBundle(ResourceBundle bundle) {
		_bundle = bundle;
	}
	
	@EventSubscriber
	public void onSFVMemberTransferEvent(SFVMemberTransferEvent sfvEvent) {
		outputSFVMemberSTOR(sfvEvent);
	}

	private void outputSFVMemberSTOR(SFVMemberTransferEvent sfvEvent) {
		Map<String, Object> env = new HashMap<>(SiteBot.GLOBAL_ENV);

		DirectoryHandle dir = sfvEvent.getDirectory();

		try {
			String username = sfvEvent.getUser().getName();
			SFVStatus sfvStatus = sfvEvent.getSFVStatus();

			//FIRST
			try {
				dir.getPluginMetaData(SFV_FIRST);
			} catch (KeyNotFoundException e) {
				// This is fine, FIRST not announced yet
				if (sfvStatus.getAvailable() >= 1 && sfvEvent.getSFVInfo().getSize() > 1) {
					dir.addPluginMetaData(SFV_FIRST, true);
					AnnounceWriter writer = _config.getPathWriter("store.first", dir);
					if (writer != null) {
						fillEnvSection(env, sfvEvent, writer, true);
						env.put("files", Integer.toString(sfvEvent.getSFVInfo().getSize()));
						env.put("expectedsize", (Bytes.formatBytes(
								SFVTools.getSFVLargestFileBytes(dir, sfvEvent.getSFVData()) * sfvEvent.getSFVInfo().getSize())));
						sayOutput(ReplacerUtils.jprintf( "sfv.store.first", env, _bundle), writer);
					}
					return;
				}
			}

			//NEW RACER
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
							env.put("filesleft",
									Integer.toString(sfvStatus.getMissing()));
							env.put("percentdone", Integer.toString(
									(sfvStatus.getPresent() * 100) / sfvEvent.getSFVInfo().getSize()) + "%");
							sayOutput(ReplacerUtils.jprintf("sfv.store.race", env, _bundle), writer);
						}
					}
				}
			}

			//HALFWAY
			try {
				dir.getPluginMetaData(SFV_HALFWAY);
			} catch (KeyNotFoundException e) {
				// This is fine, HALFWAY not announced yet
				int halfway = (int) Math.floor((double) sfvEvent.getSFVInfo().getSize() / 2);
				if ((sfvEvent.getSFVInfo().getSize() >= 4) &&
						(sfvStatus.getMissing() <= halfway)) {
					dir.addPluginMetaData(SFV_HALFWAY, true);
					AnnounceWriter writer = _config.getPathWriter("store.halfway", dir);
					if (writer != null) {
						Collection<UploaderPosition> uploaders = RankUtils.userSort(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()),
								"bytes", "high");

						UploaderPosition stat = uploaders.iterator().next();

						env.put("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
						env.put("leadfiles", Integer.toString(stat.getFiles()));
						env.put("leadsize", Bytes.formatBytes(stat.getBytes()));
						env.put("leadpercent",
								Integer.toString((stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) +
										"%");
						env.put("filesleft", Integer.toString(sfvStatus.getMissing()));

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
						fillEnvSection(env, sfvEvent, writer, false);
						sayOutput(ReplacerUtils.jprintf( "sfv.store.halfway", env, _bundle), writer);
					}
					return;
				}
			}

			//COMPLETE
			try {
				dir.getPluginMetaData(SFV_COMPLETE);
			} catch (KeyNotFoundException e) {
				// This is fine, COMPLETE not announced yet
				if (sfvStatus.isFinished()) {
					dir.addPluginMetaData(SFV_COMPLETE, true);
					TimerTask removeAnnounceMetadata = new TimerTask() {
						public void run() {
							try {
								dir.removePluginMetaData(SFV_FIRST);
								dir.removePluginMetaData(SFV_HALFWAY);
								dir.removePluginMetaData(SFV_COMPLETE);
							} catch (FileNotFoundException e) {
								// Dir gone, ignore
							}
						}
					};
					// Remove announce metadata from directory after 5s
					_timer.schedule(removeAnnounceMetadata, 5000L);
					AnnounceWriter writer = _config.getPathWriter("store.complete", dir);
					if (writer != null) {
						Collection<UploaderPosition> racers = RankUtils.userSort(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()),
								"bytes", "high");
						Collection<GroupPosition> groups = RankUtils.topFileGroup(SFVTools.getSFVFiles(dir, sfvEvent.getSFVData()));

						fillEnvSection(env, sfvEvent, writer, false);

						env.put("racers", Integer.toString(racers.size()));
						env.put("groups", Integer.toString(groups.size()));
						env.put("files", Integer.toString(sfvEvent.getSFVInfo().getSize()));
						env.put("size", Bytes.formatBytes(SFVTools.getSFVTotalBytes(dir, sfvEvent.getSFVData())));
						env.put("speed", Bytes.formatBytes(SFVTools.getXferspeed(dir, sfvEvent.getSFVData())) + "/s");
						sayOutput(ReplacerUtils.jprintf( "sfv.store.complete", env, _bundle), writer);

						// Find max users/groups to announce
						int maxUsers;
						int maxGroups;
						try {
							maxUsers = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
									getPropertiesForPlugin("zipscript.conf").getProperty("irc.sfv.maxusers", "10"));
						} catch (NumberFormatException e2) {
							logger.error("Non numeric irc.sfv.maxusers setting in zipscript.conf, using default");
							maxUsers = 10;
						}
						try {
							maxGroups = Integer.valueOf(GlobalContext.getGlobalContext().getPluginsConfig().
									getPropertiesForPlugin("zipscript.conf").getProperty("irc.sfv.maxgroups", "10"));
						} catch (NumberFormatException e2) {
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
											(stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) + "%");
							raceenv.put("alup",
									UserTransferStats.getStatsPlace("ALUP", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("monthup",
									UserTransferStats.getStatsPlace("MONTHUP", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("wkup",
									UserTransferStats.getStatsPlace("WKUP", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("dayup",
									UserTransferStats.getStatsPlace("DAYUP", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("aldn",
									UserTransferStats.getStatsPlace("ALDN", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("monthdn",
									UserTransferStats.getStatsPlace("MONTHDN", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("wkdn",
									UserTransferStats.getStatsPlace("WKDN", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							raceenv.put("daydn",
									UserTransferStats.getStatsPlace("DAYDN", raceuser,
											GlobalContext.getGlobalContext().getUserManager()));
							sayOutput(ReplacerUtils.jprintf( "sfv.store.complete.racer", raceenv, _bundle), writer);

							position++;
							if (position > maxUsers) {
								break;
							}
						}

						//add groups stats
						position = 1;

						sayOutput(ReplacerUtils.jprintf( "sfv.store.complete.group.header", env, _bundle), writer);
						for (GroupPosition stat : groups) {
							Map<String, Object> raceenv = new HashMap<>(env);

							raceenv.put("group", stat.getGroupname());
							raceenv.put("position", String.valueOf(position));
							raceenv.put("size", Bytes.formatBytes(stat.getBytes()));
							raceenv.put("files", Integer.toString(stat.getFiles()));
							raceenv.put("percent",
									Integer.toString(
											(stat.getFiles() * 100) / sfvEvent.getSFVInfo().getSize()) + "%");
							raceenv.put("speed",
									Bytes.formatBytes(stat.getXferspeed()) + "/s");

							sayOutput(ReplacerUtils.jprintf( "sfv.store.complete.group", raceenv, _bundle), writer);

							position++;
							if (position > maxGroups) {
								break;
							}
						}
					}
				}
			}
		} catch (NoAvailableSlaveException | SlaveUnavailableException e) {
			// Slave with sfv is offline
		} catch (FileNotFoundException e) {
			// SFV deleted?
		} catch (IOException e) {
			// SFV not readable
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

		TransferEvent event;

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
			if (totalsfv > 0) {
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

				logger.warn("Couldn't get SFV file in announce");
			}
		} catch (FileNotFoundException e1) {
			// The directory or file no longer exists, just fail out of the method
		}	
	}
}
