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
package org.drftpd.commands.zipscript.zip.hooks;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Properties;
import java.util.ResourceBundle;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.RankUtils;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.PostHookInterface;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.commands.dataconnection.DataConnectionHandler;
import org.drftpd.commands.dir.Dir;
import org.drftpd.commands.zipscript.zip.ZipTools;
import org.drftpd.commands.zipscript.zip.event.ZipTransferEvent;
import org.drftpd.commands.zipscript.zip.vfs.ZipscriptVFSDataZip;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.zipscript.zip.common.DizInfo;
import org.drftpd.protocol.zipscript.zip.common.DizStatus;
import org.drftpd.slave.RemoteIOException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.util.GroupPosition;
import org.drftpd.util.UploaderPosition;
import org.drftpd.vfs.DirectoryHandle;
import org.drftpd.vfs.FileHandle;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;


/**
 * @author djb61
 * @version $Id$
 */

public class ZipscriptZipPostHook extends ZipTools implements PostHookInterface {

	private static final Logger logger = LogManager.getLogger(ZipscriptZipPostHook.class);

	private ResourceBundle _bundle;

	private String _keyPrefix;

	public void initialize(StandardCommandManager cManager) {
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}

	public void doZipscriptSTORZipPostCheck(CommandRequest request, CommandResponse response) {
		// removing this in-case of a bad transfer
		/*if (response.getCode() != 226) {
			// Transfer failed, abort checks
			return;
		}*/
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("stor.zip.integrity.check.enabled", "false").equalsIgnoreCase("true")) {
			FileHandle transferFile;
			try {
				transferFile = response.getObject(DataConnectionHandler.TRANSFER_FILE);
			} catch (KeyNotFoundException e) {
				// We don't have a file, we shouldn't have ended up here but return anyway
				return;
			}
			if (!transferFile.exists()) {
				// No point checking the file as it has already been deleted (i.e. an abort)
				return;
			}

			String transferFileName = transferFile.getName();
			try {
				if (transferFile.getSize() > 0 && transferFileName.toLowerCase().endsWith(".zip")) {
                    logger.debug("Running zipscript integrity check on stored file {}", transferFileName);
					try {
						RemoteSlave rslave = transferFile.getASlaveForFunction();
						String index = ZipscriptVFSDataZip.getZipIssuer().issueZipCRCToSlave(rslave, transferFile.getPath());
						boolean ok = getZipIntegrityFromIndex(rslave, index);
						if (ok) {
							response.addComment("Zip integrity check OK");
							if (transferFile.exists()) {
								try {
									BaseFtpConnection conn = (BaseFtpConnection)request.getSession();
									RemoteSlave transferSlave = response.getObject(DataConnectionHandler.TRANSFER_SLAVE);
									InetAddress transferSlaveInetAddr =
										response.getObject(DataConnectionHandler.TRANSFER_SLAVE_INET_ADDRESS);
									char transferType = response.getObject(DataConnectionHandler.TRANSFER_TYPE);
									ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(request.getCurrentDirectory());
									GlobalContext.getEventService().publishAsync(
											new ZipTransferEvent(conn, "STOR", transferFile,
													conn.getClientAddress(), transferSlave, transferSlaveInetAddr,
													transferType, zipData, zipData.getDizInfo(), zipData.getDizStatus()));
								} catch (KeyNotFoundException e1) {
									// one or more bits of information didn't get populated correctly, have to skip the event
								} catch (IOException e) {
									// Do nothing, from user perspective STOR has completed so no point informing them
								}
							}
						} else {
							response.addComment("Zip integrity check failed, deleting file");
							try {
								transferFile.deleteUnchecked();
							} catch (FileNotFoundException e) {
								// file disappeared, not a problem as we wanted it gone anyway
							}
						}
					} catch (SlaveUnavailableException e) {
						// okay, it went offline while trying
						response.addComment("Slave went offline whilst checking zip integrity");
					} catch (RemoteIOException e) {
						response.addComment("Slave encountered an error whilst checking zip integrity");
						logger.warn("Error encountered whilst checking zip integrity",e);
					} catch (NoAvailableSlaveException e) {
						response.addComment("No available slave found to perform zip integrity check");
					}
				}
			} catch (FileNotFoundException e) {
				response.addComment("File has already been deleted, skipping zip integrity check");
			}
		}
	}

	public void doZipscriptCWDDizInfoHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// CWD failed, abort diz info
			return;
		}
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("cwd.diz.info.enabled", "false").equalsIgnoreCase("true")) {
			try {
				ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(response.getCurrentDirectory());
				DizInfo dizInfo = zipData.getDizInfo();
				response.addComment(new String(Base64.getMimeDecoder().decode(dizInfo.getString()), StandardCharsets.ISO_8859_1)); 
			} catch (FileNotFoundException e) {
				//Error fetching .diz, ignore
			} catch (IOException e) {
				//Error fetching .diz, ignore
			} catch (NoAvailableSlaveException e) {
				//Error fetching .diz, ignore
			}
		}
	}

	public void doZipscriptCWDDizStatsHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// CWD failed, abort stats
			return;
		}
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("cwd.zip.racestats.enabled", "false").equalsIgnoreCase("true")) {
			addRaceStats(request, response, response.getCurrentDirectory());
		}
	}

	public void doZipscriptSTORDizStatsHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 226) {
			// STOR failed, abort stats
			return;
		}
		Properties cfg =  GlobalContext.getGlobalContext().getPluginsConfig().
		getPropertiesForPlugin("zipscript.conf");
		if (cfg.getProperty("stor.zip.racestats.enabled", "false").equalsIgnoreCase("true")) {
			addRaceStats(request, response, request.getCurrentDirectory());
		}
	}

	public void doZipscriptDELEDizCleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 250) {
			// DELE failed, abort info
			return;
		}
		String deleFileName;
		try {
			deleFileName = response.getObject(Dir.FILENAME);
		} catch (KeyNotFoundException e) {
			// We don't have a file, we shouldn't have ended up here but return anyway
			return;
		}
		if (deleFileName.toLowerCase().endsWith(".zip")) {
			try {
				boolean noZip = true;
				// Check if there are any other zips left
				for(FileHandle file : request.getCurrentDirectory().getFilesUnchecked()) {
					if (file.getName().toLowerCase().endsWith(".zip")) {
						noZip = false;
						break;
					}
				}
				if (noZip) {
					request.getCurrentDirectory().removePluginMetaData(DizInfo.DIZINFO);
				}
			} catch(FileNotFoundException e) {
				// No inode to remove dizinfo from or dir has been deleted
			}
		}
	}

	public void doZipscriptWIPEDizCleanupHook(CommandRequest request, CommandResponse response) {
		if (response.getCode() != 200) {
			// WIPE failed, abort cleanup
			return;
		}
		String arg = request.getArgument();
		
		if (!arg.toLowerCase().endsWith(".zip")) { 
			return;
		}
		
		DirectoryHandle wipeDir;
		try {
			wipeDir = request.getCurrentDirectory().getNonExistentFileHandle(response.getObject(Dir.WIPE_PATH)).getParent();
		} catch (KeyNotFoundException e) {
			return;
		}
		
		try {
			boolean noZip = true;
			// Check if there are any other zips left
			for(FileHandle file : wipeDir.getFilesUnchecked()) {
				if (file.getName().toLowerCase().endsWith(".zip")) {
					noZip = false;
					break;
				}
			}
			if (noZip) {
				wipeDir.removePluginMetaData(DizInfo.DIZINFO);
			}
		} catch(FileNotFoundException e) {
			// No inode to remove dizinfo from or dir has been deleted
		}
	}	
	
	private void addRaceStats(CommandRequest request, CommandResponse response, DirectoryHandle dir) {
		// show race stats
		try {
			ZipscriptVFSDataZip zipData = new ZipscriptVFSDataZip(dir);
			DizInfo dizInfo = zipData.getDizInfo();
			DizStatus dizStatus = zipData.getDizStatus();

			Collection<UploaderPosition> racers = RankUtils.userSort(getZipFiles(dir),
					"bytes", "high");
			Collection<GroupPosition> groups = RankUtils.topFileGroup(getZipFiles(dir));

			String racerline = _bundle.getString(_keyPrefix+"cwd.racers.body");
			String groupline = _bundle.getString(_keyPrefix+"cwd.groups.body");

			ReplacerEnvironment env = request.getSession().getReplacerEnvironment(null,
					request.getSession().getUserNull(request.getUser()));

			//Start building race message
			StringBuilder raceTextBuilder = new StringBuilder(_bundle.getString(_keyPrefix+"cwd.racestats.header"));
			raceTextBuilder.append('\n');
			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.racers.header"));
			raceTextBuilder.append('\n');

			ReplacerFormat raceformat = null;

			//Add racer stats
			int position = 1;

			for (UploaderPosition stat : racers) {
				User raceuser;

				try {
					raceuser = GlobalContext.getGlobalContext().getUserManager()
					.getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					continue;
				} catch (UserFileException e2) {
					logger.error("Error reading userfile", e2);

					continue;
				}

				ReplacerEnvironment raceenv = new ReplacerEnvironment();

				raceenv.add("speed",
						Bytes.formatBytes(stat.getXferspeed()) + "/s");
				raceenv.add("user", stat.getUsername());
				raceenv.add("group", raceuser.getGroup());
				raceenv.add("files", "" + stat.getFiles());
				raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("position", String.valueOf(position));
				raceenv.add("percent",
						Integer.toString(
								(stat.getFiles() * 100) / dizInfo.getTotal()) + "%");

				try {
					raceTextBuilder.append(SimplePrintf.jprintf(racerline,raceenv));
					raceTextBuilder.append('\n');
					position++;
				} catch (FormatterException e) {
					logger.warn(e);
				}
			}

			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.racers.footer"));
			raceTextBuilder.append('\n');
			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.groups.header"));
			raceTextBuilder.append('\n');

			//add groups stats
			position = 1;

			for (GroupPosition stat: groups) {
				ReplacerEnvironment raceenv = new ReplacerEnvironment();

				raceenv.add("group", stat.getGroupname());
				raceenv.add("position", String.valueOf(position));
				raceenv.add("bytes", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("files", Integer.toString(stat.getFiles()));
				raceenv.add("percent",
						Integer.toString(
								(stat.getFiles() * 100) / dizInfo.getTotal()) + "%");
				raceenv.add("speed",
						Bytes.formatBytes(stat.getXferspeed()) + "/s");

				try {
					raceTextBuilder.append(SimplePrintf.jprintf(groupline,raceenv));
					raceTextBuilder.append('\n');
					position++;
				} catch (FormatterException e) {
					logger.warn(e);
				}
			}

			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.groups.footer"));
			raceTextBuilder.append('\n');

			env.add("completefiles", Integer.toString(dizStatus.getPresent()) + "/" + Integer.toString(dizInfo.getTotal()));
			env.add("totalbytes", Bytes.formatBytes(getZipTotalBytes(dir)));
			env.add("totalspeed",
					Bytes.formatBytes(getXferspeed(dir)) + "/s");
			env.add("totalpercent",
					Integer.toString(
							(getZipFiles(dir).size() * 100) / dizInfo.getTotal()) +
			"%");

			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.totals.body"));
			raceTextBuilder.append('\n');
			raceTextBuilder.append(_bundle.getString(_keyPrefix+"cwd.racestats.footer"));
			raceTextBuilder.append('\n');

			try {
				raceformat = ReplacerFormat.createFormat(raceTextBuilder.toString());
			} catch (FormatterException e1) {
				logger.warn(e1);
			}

			try {
				if (raceformat == null) {
					response.addComment("cwd.uploaders");
				} else {
					response.addComment(SimplePrintf.jprintf(raceformat, env));
				}
			} catch (FormatterException e) {
				response.addComment("cwd.uploaders");
				logger.warn("", e);
			}
		} catch (FileNotFoundException e) {
			//Error fetching SFV, ignore
		} catch (IOException e) {
			//Error fetching SFV, ignore
		} catch (NoAvailableSlaveException e) {
			//Error fetching SFV, ignore
		}
	}
}
