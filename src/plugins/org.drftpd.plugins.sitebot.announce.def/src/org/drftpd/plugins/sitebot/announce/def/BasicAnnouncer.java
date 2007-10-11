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
package org.drftpd.plugins.sitebot.announce.def;

import java.io.FileNotFoundException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.TreeSet;

import org.apache.log4j.Logger;
import org.bushe.swing.event.EventSubscriber;
import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.RankUtils;
import org.drftpd.Time;
import org.drftpd.commands.slavemanagement.SlaveManagement;
import org.drftpd.commands.zipscript.SFVTools;
import org.drftpd.commands.zipscript.vfs.ZipscriptVFSDataSFV;
import org.drftpd.event.DirectoryFtpEvent;
import org.drftpd.event.SlaveEvent;
import org.drftpd.event.TransferEvent;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.plugins.sitebot.AnnounceInterface;
import org.drftpd.plugins.sitebot.AnnounceWriter;
import org.drftpd.plugins.sitebot.OutputWriter;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.plugins.sitebot.config.AnnounceConfig;
import org.drftpd.plugins.sitebot.config.ChannelConfig;
import org.drftpd.plugins.sitebot.event.InviteEvent;
import org.drftpd.protocol.zipscript.common.SFVInfo;
import org.drftpd.protocol.zipscript.common.SFVStatus;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.usermanager.util.UserTransferStats;
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
public class BasicAnnouncer extends SFVTools implements AnnounceInterface, EventSubscriber {

	private static final Logger logger = Logger.getLogger(BasicAnnouncer.class);

	private AnnounceConfig _config;

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialise(AnnounceConfig config, ResourceBundle bundle) {
		_config = config;
		_bundle = bundle;
		_keyPrefix = this.getClass().getName();
		GlobalContext.getEventService().subscribe(DirectoryFtpEvent.class, this);
		GlobalContext.getEventService().subscribe(InviteEvent.class, this);
		GlobalContext.getEventService().subscribe(SlaveEvent.class, this);
	}

	public void stop() {
		GlobalContext.getEventService().unsubscribe(DirectoryFtpEvent.class, this);
		GlobalContext.getEventService().unsubscribe(InviteEvent.class, this);
		GlobalContext.getEventService().unsubscribe(SlaveEvent.class, this);
	}

	public String[] getEventTypes() {
		String[] types = {"mkdir","request","reqfilled","rmdir","wipe","pre","addslave",
				"delslave","store","invite"};
		return types;
	}

	public void onEvent(Object event) {
		if (event instanceof DirectoryFtpEvent) {
			handleDirectoryFtpEvent((DirectoryFtpEvent) event);
		} else if (event instanceof SlaveEvent) {
			handleSlaveEvent((SlaveEvent) event);
		} else if (event instanceof InviteEvent) {
			handleInviteEvent((InviteEvent) event);
		}
	}

	private void handleDirectoryFtpEvent(DirectoryFtpEvent direvent) {
		// TODO We should decide if we are going to check path permissions when
		// receiving an event or before sending it, including it here for now until
		// that decision is made
		if (!GlobalContext.getConfig().
				checkPathPermission("dirlog", direvent.getUser(), direvent.getDirectory())) {
			return;
		}

		if ("MKD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "mkdir");
		} else if ("REQUEST".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "request");
		} else if ("REQFILLED".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "reqfilled");
		} else if ("RMD".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "rmdir");
		} else if ("WIPE".equals(direvent.getCommand())) {
			if (direvent.getDirectory().isDirectory()) {
				outputDirectoryEvent(direvent, "wipe");
			}
		} else if ("PRE".equals(direvent.getCommand())) {
			outputDirectoryEvent(direvent, "pre");
		} else if ("STOR".equals(direvent.getCommand())) {
			outputDirectorySTOR((TransferEvent) direvent, "store");
		}
	}

	private void handleSlaveEvent(SlaveEvent event) {
		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
		env.add("slave", event.getRSlave().getName());
		env.add("message", event.getMessage());

		if (event.getCommand().equals("ADDSLAVE")) {
			SlaveStatus status;

			try {
				status = event.getRSlave().getSlaveStatusAvailable();
			} catch (SlaveUnavailableException e) {
				logger.warn("in ADDSLAVE event handler", e);

				return;
			}

			SlaveManagement.fillEnvWithSlaveStatus(env, status);

			outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".addslave", env, _bundle), "addslave");
		} else if (event.getCommand().equals("DELSLAVE")) {
			outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".delslave", env, _bundle), "delslave");
		}
	}

	private void handleInviteEvent(InviteEvent event) {
		if (event.getTargetBot() == null || _config.getBot().getBotName().equalsIgnoreCase(event.getTargetBot())) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
			env.add("user", event.getUser().getName());
			env.add("nick", event.getIrcNick());
			if (event.getCommand().equals("INVITE")) {
				outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".invite.success", env, _bundle), "invite");
				for (ChannelConfig chan : _config.getBot().getConfig().getChannels()) {
					if (chan.isPermitted(event.getUser())) {
						_config.getBot().sendInvite(event.getIrcNick(), chan.getName());
					}
				}
			} else if (event.getCommand().equals("BINVITE")) {
				outputSimpleEvent(ReplacerUtils.jprintf(_keyPrefix+".invite.failed", env, _bundle), "invite");
			}
		}
	}

	private void outputDirectorySTOR(TransferEvent fileevent, String type) {
		AnnounceWriter writer = _config.getPathWriter(type, fileevent.getDirectory());
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

			DirectoryHandle dir = fileevent.getDirectory();

			// ANNOUNCE NFO FILE
			if (fileevent.getTransferFile().getName().toLowerCase().endsWith(".nfo")) {
				fillEnvSection(env, fileevent, writer, true); 
				sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.nfo", env, _bundle), writer);
			} 

			SFVInfo sfvinfo;
			ZipscriptVFSDataSFV sfvData = new ZipscriptVFSDataSFV(dir);

			try {
				sfvinfo = sfvData.getSFVInfo();

				// throws IOException, ObjectNotFoundException, NoAvailableSlaveException
			} catch (FileNotFoundException ex) {
				logger.info("No sfv file in " + dir.getPath() +
				", can't publish race info");
				return;
			} catch (NoAvailableSlaveException e) {
				logger.info("No available slave with .sfv");
				return;
			}

			if (sfvinfo.getEntries().get(fileevent.getTransferFile().getName()) == null) {
				return;
			}

			int halfway = (int) Math.floor((double) sfvinfo.getSize() / 2);

			try {
				///// start ///// start ////
				String username = fileevent.getUser().getName();
				SFVStatus sfvstatus = sfvData.getSFVStatus();
				// ANNOUNCE FIRST FILE RCVD 
				//   and EXPECTING xxxMB in xxx Files on same line.
				if(sfvstatus.getAvailable() == 1) {
					fillEnvSection(env, fileevent, writer, true);
					env.add("files", Integer.toString(sfvinfo.getSize()));
					env.add("expectedsize", (Bytes.formatBytes(getSFVTotalBytes(dir,sfvData) * sfvinfo.getSize())));
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.first", env, _bundle), writer);
				}
				//check if new racer
				if ((sfvinfo.getSize() - sfvstatus.getMissing()) != 1) {
					for (Iterator<FileHandle> iter = getSFVFiles(dir, sfvData).iterator(); iter.hasNext();) {
						FileHandle sfvFileEntry = iter.next();

						if (sfvFileEntry == fileevent.getTransferFile())
							continue;

						if (sfvFileEntry.getUsername().equals(username)
								&& sfvFileEntry.getXfertime() >= 0) {
							break;
						}

						if (!iter.hasNext()) {
							fillEnvSection(env, fileevent, writer, true);
							env.add("filesleft",
									Integer.toString(sfvstatus.getMissing()));
							sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.embraces", env, _bundle), writer);
						}
					}
				}

				//COMPLETE
				if (sfvstatus.isFinished()) {
					Collection<UploaderPosition> racers = RankUtils.userSort(getSFVFiles(dir, sfvData),
							"bytes", "high");
					Collection<GroupPosition> groups = RankUtils.topFileGroup(getSFVFiles(dir, sfvData));

					fillEnvSection(env, fileevent, writer, false);

					env.add("racers", Integer.toString(racers.size()));
					env.add("groups", Integer.toString(groups.size()));
					env.add("files", Integer.toString(sfvinfo.getSize()));
					env.add("size", Bytes.formatBytes(getSFVTotalBytes(dir, sfvData)));
					env.add("speed", Bytes.formatBytes(getXferspeed(dir, sfvData)) + "/s");
					sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete", env, _bundle), writer);

					//// store.complete.racer ////

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
										(stat.getFiles() * 100) / sfvinfo.getSize()) + "%");
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
										(stat.getFiles() * 100) / sfvinfo.getSize()) + "%");
						raceenv.add("speed",
								Bytes.formatBytes(stat.getXferspeed()) + "/s");

						sayOutput(ReplacerUtils.jprintf(_keyPrefix+".store.complete.group", raceenv, _bundle), writer);
					}

					//HALFWAY
				} else if ((sfvinfo.getSize() >= 4) &&
						(sfvstatus.getMissing() == halfway)) {
					Collection<UploaderPosition> uploaders = RankUtils.userSort(getSFVFiles(dir, sfvData),
							"bytes", "high");

					UploaderPosition stat = uploaders.iterator().next();

					env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
					env.add("leadfiles", Integer.toString(stat.getFiles()));
					env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
					env.add("leadpercent",
							Integer.toString((stat.getFiles() * 100) / sfvinfo.getSize()) +
					"%");
					env.add("filesleft", Integer.toString(sfvstatus.getMissing()));

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
			} catch (NoAvailableSlaveException e) {
				// Slave with sfv is offline
			} catch (FileNotFoundException e) {
				// SFV deleted?
			}
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

	private void outputSimpleEvent(String output, String type) {
		AnnounceWriter writer = _config.getSimpleWriter(type);
		// Check we got a writer back, if it is null do nothing and ignore the event
		if (writer != null) {
			sayOutput(output, writer);
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
				FileHandle oldestFile = getOldestFile(dir);
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

			if (event.getCommand().equals("PRE") == false) {
				try {
					sfvinfo = sfvData.getSFVInfo();
					totalsfv += 1;
					totalfiles += sfvinfo.getSize();
					totalbytes += getSFVTotalBytes(dir,sfvData);
					totalxfertime += getSFVTotalXfertime(dir,sfvData);
				} catch (Exception e1) {
					// Failed to get sfv data, safe to continue, that data
					// will just not be available
				}
			} else {
				// TODO Add in recursive summing of all subdir sfvs for pre
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

	// TODO May want to relocate this somewhere else if it looks like it may be of use
	// elsewhere

	private FileHandle getOldestFile(DirectoryHandle dir) throws FileNotFoundException {
		TreeSet<FileHandle> files = new TreeSet<FileHandle>(new FileAgeComparator());
		files.addAll(dir.getFilesUnchecked());
		return files.first();
	}

	class FileAgeComparator implements Comparator<FileHandle> {

		public int compare(FileHandle f1, FileHandle f2) {

			try {
				return ((f1.lastModified() < f2.lastModified()) ? (-1) : ((f1.lastModified() == f2.lastModified()) ? 0
						: 1));
			}
			catch (FileNotFoundException e) {
				return 0;
			}
		}
	}
}
