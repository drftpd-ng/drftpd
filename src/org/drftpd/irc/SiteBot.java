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
package org.drftpd.irc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.MissingResourceException;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TreeMap;
import java.util.Vector;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.util.ReplacerUtils;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.drftpd.Bytes;
import org.drftpd.PropertyHelper;
import org.drftpd.RankUtils;
import org.drftpd.SFVStatus;
import org.drftpd.Time;
import org.drftpd.commands.UserManagement;
import org.drftpd.id3.ID3Tag;
import org.drftpd.irc.utils.Channel;
import org.drftpd.irc.utils.CommandList;
import org.drftpd.irc.utils.IRCCommands;
import org.drftpd.master.SlaveManager;
import org.drftpd.misc.CaseInsensitiveHashMap;
import org.drftpd.nuke.NukeBeans;
import org.drftpd.nuke.NukeData;
import org.drftpd.nuke.NukeUtils;
import org.drftpd.nuke.Nukee;
import org.drftpd.sections.SectionInterface;
import org.drftpd.slave.SlaveStatus;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;
import org.drftpd.vfs.InodeHandle;
import org.schwering.irc.lib.IRCConnection;
import org.schwering.irc.lib.IRCEventListener;
import org.schwering.irc.lib.IRCUser;
import org.schwering.irc.lib.IRCUtil;
import org.schwering.irc.lib.SSLIRCConnection;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

/**
 * @author mog
 * @author fr0w
 */
public class SiteBot extends FtpListener {
	
	public static final ReplacerEnvironment GLOBAL_ENV = new ReplacerEnvironment();
	
	static {
		GLOBAL_ENV.add("bold", IRCUtil.boldIndicator);
		GLOBAL_ENV.add("coloroff", IRCUtil.colorEndIndicator);
		GLOBAL_ENV.add("color", IRCUtil.colorIndicator);
		GLOBAL_ENV.add("underline", IRCUtil.underlinedIndicator);
	}
	
	private static final Logger logger = Logger.getLogger(SiteBot.class);
	
	private boolean _enableAnnounce;
	private boolean _useSSL;
	private boolean _autoReconnect = true;
	
	private IRCCommands _ircCommands;
	private Hashtable<String, SectionSettings> _sections;
	private Vector<WhoisEntry> _identWhoisList = new Vector<WhoisEntry>();
	private CaseInsensitiveHashMap<String,Channel> _channelMap;
	
	private IRCConnection _conn = null;
	
	private int _maxUserAnnounce;
	private int _maxGroupAnnounce;
	private int _portMin;
	private int _portMax;
	
	private long _connRetry = 0;
	private long _connRetryMax;
	
	private String _server;
	private String _nick;
	private String _user;
	private String _realname;
	private String _pass;
	private String _psyBNC;
	
	private String _perform;
	
	private String _primaryChannelName;
	private String _inviteEventChannel;
	private String _slaveEventChannel;
	private String _shutdownEventChannel;
	
	private int _connDelay;
	
	private Timer _timer = new Timer("BotTimer");
	private Tasker _tasker = new Tasker(this);
	
	private Properties _properties;
	
	private HashMap<Class, IRCEventListener> _listeners = new HashMap<Class, IRCEventListener>();
	
	public SiteBot() {
		super();
	}
	
	/**
	 * Accepts multiple destinations.<br>
	 * Ex:<br>
	 * dests = #chan1 #chan2 #chan3<br>
	 * Will send the message to this 3 channels.
	 * @param dests
	 * @param msg
	 */
	private void sayArray(String dests, String msg) {
		StringTokenizer st = new StringTokenizer(dests.replace(",",""), " ");
		while (st.hasMoreTokens())
			say(st.nextToken(), msg);
	}
	
	/**
	 * Send the message to the correct channels according to type of event.<br>
	 * If the specified event does not have an array of channels, it will
	 * simply use sayGlobal().<br>
	 * Accepts multiple chans. 
	 * @see sayArray() and sayGlobal().
	 * @param event
	 * @param msg
	 */
	private void sayEvent(String event, String msg) {
		if (event.equals("") || event == null)
			sayGlobal(msg);
		else { 
			event = event.toLowerCase();
			if (event.equals("shutdown")) 
				sayArray(_shutdownEventChannel, msg);
			else if (event.equals("slave"))
				sayArray(_slaveEventChannel, msg);
		}
	}
	
	/**
	 * Handles all FTPListener activity.
	 */
	public void actionPerformed(Event event) {
		try {
			if (event.getCommand().equals("RELOAD")) {
				try {
					reload();
				} catch (IOException e) {
					logger.warn("", e);
				}
			} else if (isConnected()) { // not smart processing this if
				// the bot is offline
				if (event.getCommand().equals("SHUTDOWN")) {
					MessageEvent mevent = (MessageEvent) event;
					ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
					env.add("message", mevent.getMessage());					
					sayEvent("shutdown", ReplacerUtils.jprintf("shutdown", env, SiteBot.class));
				} else if (event instanceof InviteEvent) {
					actionPerformedInvite((InviteEvent) event);
				} else if (_enableAnnounce) {
					if (event instanceof DirectoryFtpEvent) {
						actionPerformedDirectory((DirectoryFtpEvent) event);
					} else if (event instanceof NukeEvent) {
						actionPerformedNuke((NukeEvent) event);
					} else if (event instanceof SlaveEvent) {
						actionPerformedSlave((SlaveEvent) event);
					}
				}
			}
		} catch (FormatterException ex) {
			logger.warn("", ex);
		}
	}
	
	private void actionPerformedDirectory(DirectoryFtpEvent direvent)
	throws FormatterException {
		if (!getGlobalContext().getConfig().checkPathPermission("dirlog", direvent.getUser(), direvent.getDirectory())) {
			return;
		}
		
		if ("MKD".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "mkdir");
		} else if ("REQUEST".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "request");
		} else if ("REQFILLED".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "reqfilled");
		} else if ("REQDEL".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "reqdel");
		} else if ("RMD".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "rmdir");
		} else if ("WIPE".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "wipe");
		} else if ("PRE".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "pre");
		} else if ("STOR".equals(direvent.getCommand())) {
			actionPerformedDirectorySTOR((TransferEvent) direvent);
		}
	}
	
	private void actionPerformedDirectoryID3(TransferEvent direvent)
	throws FormatterException {
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		InodeHandle dir;
		
		try {
			dir = direvent.getDirectory().getParent();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
		
		ID3Tag id3tag;
		
		try {
			id3tag = dir.lookupFile(dir.lookupMP3File()).getID3v1Tag();
		} catch (FileNotFoundException ex) {
			logger.info("No id3tag info for " +
					direvent.getDirectory().getPath() +
			", can't publish id3tag info");
			
			return;
		} catch (NoAvailableSlaveException e) {
			logger.info("No available slave with id3 info");
			
			return;
		} catch (IOException e) {
			logger.warn("IO error reading id3 info", e);
			
			return;
		}
		
		env.add("path", dir.getName());
		env.add("genre", id3tag.getGenre().trim());
		env.add("year", id3tag.getYear().trim());
		env.add("album", id3tag.getAlbum().trim());
		env.add("artist", id3tag.getArtist().trim());
		env.add("title", id3tag.getTitle().trim());
		
		Ret ret = getPropertyFileSuffix("id3tag", dir);
		fillEnvSection( env, direvent, ret.getSection(), direvent.getDirectory());
		say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
	}
	
	private void actionPerformedDirectorySTOR(TransferEvent direvent)
	throws FormatterException {
		
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		LinkedRemoteFile dir;
		
		try {
			dir = direvent.getDirectory().getParentFile();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
		
		// Not sure how useful this is.
		if (direvent.getDirectory().isDeleted())
			return;
		
		// ANNOUNCE NFO FILE
		if (direvent.getDirectory().getName().toLowerCase().endsWith(".nfo")) {
			Ret ret = getPropertyFileSuffix("store.nfo", dir); 
			fillEnvSection(env, direvent, ret.getSection()); 
			say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
		} 
		
		SFVFile sfvfile;
		
		try {
			sfvfile = dir.lookupSFVFile();
		} catch (FileNotFoundException ex) {
			logger.info("No sfv file in " + direvent.getDirectory().getPath() +	", can't publish race info");			
			return;
		} catch (NoAvailableSlaveException e) {
			logger.info("No available slave with .sfv");			
			return;
		} catch (IOException e) {
			logger.warn("IO error reading .sfv", e);			
			return;
		}
		
		if (!sfvfile.hasFile(direvent.getDirectory().getName())) {
			return;
		}
		
		int halfway = (int) Math.floor((double) sfvfile.size() / 2);
		
		// we don't need to have an sfv in order to announce the mp3 info
		// TODO
		if (sfvfile.getStatus().getPresent() == 1) {
			actionPerformedDirectoryID3(direvent);
		}
		
		///// start ///// start ////
		String username = direvent.getUser().getName();
		SFVStatus sfvstatus = sfvfile.getStatus();
		// ANNOUNCE FIRST FILE RCVD 
		//   and EXPECTING xxxMB in xxx Files on same line.
		if( sfvstatus.getAvailable() == 1) {
			Ret ret = getPropertyFileSuffix("store.first", dir);
			fillEnvSection(env, direvent, ret.getSection(), direvent.getDirectory());
			env.add("files", Integer.toString(sfvfile.size()));
			env.add("expectedsize", (Bytes.formatBytes(sfvfile.getTotalBytes() * sfvfile.size())));
			say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
		}
		
		// Check if new racer
		if ((sfvfile.size() - sfvstatus.getMissing()) != 1) {
			for (Iterator iter = sfvfile.getDirectories().iterator(); iter.hasNext();) {
				LinkedRemoteFile sfvFileEntry = (LinkedRemoteFile) iter.next();
				
				// If file just uploaded was deleted (by ZipScript?), move on and do nothing.
				if (direvent.getDirectory().isDeleted())
					break;
				
				if (sfvFileEntry == direvent.getDirectory())
					continue;
				
				if (sfvFileEntry.getUsername().equals(username)
						&& sfvFileEntry.getXfertime() >= 0) {
					break;
				}
				
				if (!iter.hasNext()) {
					Ret ret = getPropertyFileSuffix("store.embraces",
							direvent.getDirectory());
					String format = ret.getFormat();
					fillEnvSection(env, direvent, ret.getSection(),
							direvent.getDirectory());
					env.add("filesleft",
							Integer.toString(sfvstatus.getMissing()));
					
					say(ret.getSection(), SimplePrintf.jprintf(format, env));
				}
			}
		}
		
		//COMPLETE
		if (sfvstatus.isFinished()) {
			Collection racers = RankUtils.userSort(sfvfile.getDirectories(), "bytes", "high");
			Collection groups = RankUtils.topFileGroup(sfvfile.getDirectories());
			
			//Collection fast = userSort(sfvfile.getFiles(), "xferspeed", "high");
			//Collection slow = userSort(sfvfile.getFiles(), "xferspeed", "low");
			Ret ret = getPropertyFileSuffix("store.complete", dir);
			
			try {
				fillEnvSection(env, direvent, ret.getSection(),
						direvent.getDirectory().getParentFile());
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			
			env.add("racers", Integer.toString(racers.size()));
			env.add("groups", Integer.toString(groups.size()));
			env.add("files", Integer.toString(sfvfile.size()));
			env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()));
			env.add("speed", Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
			say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
			
			//// store.complete.racer ////
			ret = getPropertyFileSuffix("store.complete.racer", dir);
			
			ReplacerFormat raceformat;
			
			// already have section from ret.section
			raceformat = ReplacerFormat.createFormat(ret.getFormat());
			
			try {
				fillEnvSection(env, direvent, ret.getSection(),
						direvent.getDirectory().getParentFile());
			} catch (FileNotFoundException e) {
				throw new RuntimeException(e);
			}
			
			int position = 1;
			
			for (Iterator iter = racers.iterator(); iter.hasNext();) {
				UploaderPosition stat = (UploaderPosition) iter.next();
				
				User raceuser;
				
				try {
					raceuser = getGlobalContext().getUserManager()
					.getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);					
					continue;
				}
				
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("section", ret.getSection().getName());
				raceenv.add("user", raceuser.getName());
				raceenv.add("group", raceuser.getGroup());
				
				raceenv.add("position", new Integer(position++));
				raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("files", Integer.toString(stat.getFiles()));
				raceenv.add("percent",
						Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
				"%");
				raceenv.add("speed",
						Bytes.formatBytes(stat.getXferspeed()) + "/s");
				raceenv.add("alup",
						new Integer(TransferStatistics.getStatsPlace("ALUP",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("monthup",
						new Integer(TransferStatistics.getStatsPlace("MONTHUP",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("wkup",
						new Integer(TransferStatistics.getStatsPlace("WKUP",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("dayup",
						new Integer(TransferStatistics.getStatsPlace("DAYUP",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("aldn",
						new Integer(TransferStatistics.getStatsPlace("ALDN",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("monthdn",
						new Integer(TransferStatistics.getStatsPlace("MONTHDN",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("wkdn",
						new Integer(TransferStatistics.getStatsPlace("WKDN",
								raceuser, getGlobalContext().getUserManager())));
				raceenv.add("daydn",
						new Integer(TransferStatistics.getStatsPlace("DAYDN",
								raceuser, getGlobalContext().getUserManager())));
				
				say(ret.getSection(), SimplePrintf.jprintf(raceformat, raceenv));
				if (position >= _maxUserAnnounce)
					break;
			}
			
			Ret ret3 = getPropertyFileSuffix("store.complete.group", dir);
			
			// already have section from ret.section
			raceformat = ReplacerFormat.createFormat(ret3.getFormat());
			say(ret.getSection(),
					SimplePrintf.jprintf(getPropertyFileSuffix(
							"store.complete.group.header", dir).getFormat(), env));
			
			position = 1;
			
			for (Iterator iter = groups.iterator(); iter.hasNext();) {
				GroupPosition stat = (GroupPosition) iter.next();
				
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("section", ret.getSection().getName());
				raceenv.add("group", stat.getGroupname());
				
				raceenv.add("position", new Integer(position++));
				raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("files", Integer.toString(stat.getFiles()));
				raceenv.add("percent",
						Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
				"%");
				raceenv.add("speed",
						Bytes.formatBytes(stat.getXferspeed()) + "/s");
				
				say(ret.getSection(), SimplePrintf.jprintf(raceformat, raceenv));
				if (position >= _maxGroupAnnounce)
					break;
			}
			
			//HALFWAY
		} else if ((sfvfile.size() >= 4) &&
				(sfvstatus.getMissing() == halfway)) {
			Collection uploaders = RankUtils.userSort(sfvfile.getDirectories(), "bytes", "high");
			
			//			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			UploaderPosition stat = (UploaderPosition) uploaders.iterator()
			.next();
			
			env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
			env.add("leadfiles", Integer.toString(stat.getFiles()));
			env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
			env.add("leadpercent",
					Integer.toString((stat.getFiles() * 100) / sfvfile.size()) +
			"%");
			env.add("filesleft", Integer.toString(sfvstatus.getMissing()));
			
			User leaduser = null;
			try {
				leaduser = getGlobalContext().getUserManager()
				.getUserByName(stat.getUsername());
			} catch (NoSuchUserException e3) {
				logger.log(Level.WARN, "", e3);
			} catch (UserFileException e3) {
				logger.log(Level.WARN, "", e3);
			}
			env.add("leaduser", leaduser != null ? leaduser.getName() : stat.getUsername());
			env.add("leadgroup", leaduser != null ? leaduser.getGroup() : "");
			
			Ret ret = getPropertyFileSuffix("store.halfway", dir);
			fillEnvSection(env, direvent, ret.getSection());
			
			say(ret.getSection(), SimplePrintf.jprintf(ret.getFormat(), env));
		}
	}
	
	private void actionPerformedInvite(InviteEvent event) throws FormatterException {
		String nick = event.getIrcNick();
		
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		env.add("user",event.getUser());
		env.add("nick",event.getIrcNick());
		
		if ("INVITE".equals(event.getCommand()) ||
				"SITE INVITE".equals(event.getCommand())) {
			
			ReplacerFormat format = ReplacerUtils.finalFormat(SiteBot.class,"invite.success");
			logger.info("Invited " + nick);
			
			// will prevent double spam of invites.
			boolean spammed = false;
			
			if (_inviteEventChannel != null || !_inviteEventChannel.equals("")) {
				sayArray(_inviteEventChannel, SimplePrintf.jprintf(format, env));
				spammed = true;
			}
			
			for (Iterator iter = getCurrentChannels().iterator(); iter.hasNext();) {
				Channel chan = (Channel) iter.next();
				
				if (chan != null) {
					if (chan.hasOp()) {            		
						if (chan.checkPerms(event.getUser())) {
							_conn.doInvite(nick, chan.getName());
							
							if (!spammed)
								say(chan.getName(),SimplePrintf.jprintf(format, env));
							
							try {
								notice(nick, "Channel key for " + chan.getName() + " is " + chan.getChannelKey(event.getUser()));
							} catch (ObjectNotFoundException execption) {
								// no key or not enough permissions
							}
						} else {
							logger.info("User does not have enough permissions to invite into " + chan.getName());
						}
					} 
				} else {
					logger.error("Could not find Channel for " + chan.getName() + " this is a bug, please report it!", new Throwable());
				}
			}
			
			_identWhoisList.add(new WhoisEntry(nick,event.getUser()));			
			
			logger.info("Looking up "+ nick + " to set IRCIdent");
			_conn.doWhois(nick);        	
		} else if ("BINVITE".equals(event.getCommand())) {
			ReplacerFormat format = ReplacerUtils.finalFormat(SiteBot.class,"invite.failed");
			if (_inviteEventChannel != null || !_inviteEventChannel.equals(""))
				sayArray(_inviteEventChannel, SimplePrintf.jprintf(format, env));
			else
				sayGlobal(SimplePrintf.jprintf(format, env));
		}
		
	}
	
	private void actionPerformedNuke(NukeEvent event) throws FormatterException {
		String cmd = event.getCommand();
		NukeData nukeData = event.getNukeData();
		SectionInterface section = getGlobalContext().getSectionManager()
		.lookup(event.getPath());
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		
		env.add("size", Bytes.formatBytes(event.getSize()));
		env.add("section", section.getName());
		
		env.add("path", event.getPath());
		env.add("reason", event.getReason());
		env.add("multiplier", String.valueOf(event.getMultiplier()));
		
		env.add("user", event.getUser().getName());
		env.add("group", event.getUser().getGroup());
		
		List<Nukee> nukees = NukeBeans.getNukeeList(nukeData);
		if (cmd.equals("NUKE")) {
			say(section, ReplacerUtils.jprintf("nuke", env, SiteBot.class));
			
			ReplacerFormat raceformat = ReplacerUtils.finalFormat(SiteBot.class,
			"nuke.nukees");
			
			int position = 1;
			long nobodyAmount = 0;
			
			for (Iterator iter = nukees.iterator(); iter.hasNext();) {
				Nukee stat = (Nukee) iter.next();
				
				User raceuser;
				
				try {
					raceuser = getGlobalContext().getUserManager()
					.getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					nobodyAmount += stat.getAmount();					
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}
				
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("user", raceuser.getName());
				raceenv.add("group", raceuser.getGroup());
				
				raceenv.add("position", "" + position++);
				raceenv.add("size", Bytes.formatBytes(stat.getAmount()));
				
				long nukedAmount = NukeUtils.calculateNukedAmount(stat.getAmount(),
						raceuser.getKeyedMap().getObjectFloat(UserManagement.RATIO),
						event.getMultiplier());
				raceenv.add("nukedamount", Bytes.formatBytes(nukedAmount));
				say(section, SimplePrintf.jprintf(raceformat, raceenv));
			}
			
			if (nobodyAmount != 0) {
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("user", "nobody");
				raceenv.add("group", "nogroup");
				
				raceenv.add("position", "?");
				raceenv.add("size", Bytes.formatBytes(nobodyAmount));
				
				say(section, SimplePrintf.jprintf(raceformat, raceenv));
			}
		} else if (cmd.equals("UNNUKE")) {
			say(section, ReplacerUtils.jprintf("unnuke", env, SiteBot.class));
			
			ReplacerFormat raceformat = ReplacerUtils.finalFormat(SiteBot.class,
			"unnuke.nukees");
			
			int position = 1;
			long nobodyAmount = 0;
			
			for (Iterator iter = nukees.iterator(); iter.hasNext();) {
				Nukee stat = (Nukee) iter.next();
				
				User raceuser;
				
				try {
					raceuser = getGlobalContext().getUserManager()
					.getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					nobodyAmount += stat.getAmount();					
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);					
					continue;
				}
				
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("user", raceuser.getName());
				raceenv.add("group", raceuser.getGroup());
				
				raceenv.add("position", "" + position++);
				raceenv.add("size", Bytes.formatBytes(stat.getAmount()));
				
				long nukedamount = NukeUtils.calculateNukedAmount(stat.getAmount(),
						raceuser.getKeyedMap().getObjectFloat(UserManagement.RATIO),
						event.getMultiplier());
				raceenv.add("nukedamount", Bytes.formatBytes(nukedamount));
				say(section, SimplePrintf.jprintf(raceformat, raceenv));
			}
			
			if (nobodyAmount != 0) {
				ReplacerEnvironment raceenv = new ReplacerEnvironment(GLOBAL_ENV);
				
				raceenv.add("user", "nobody");
				raceenv.add("group", "nogroup");
				
				raceenv.add("position", "?");
				raceenv.add("size", Bytes.formatBytes(nobodyAmount));
				
				say(section, SimplePrintf.jprintf(raceformat, raceenv));
			}
			
		}
	}
	
	private void actionPerformedSlave(SlaveEvent event)
	throws FormatterException {
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
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
			
			fillEnvSlaveStatus(env, status, getSlaveManager());
			
			sayEvent("slave", ReplacerUtils.jprintf("addslave", env, SiteBot.class));
		} else if (event.getCommand().equals("DELSLAVE")) {
			sayEvent("slave", ReplacerUtils.jprintf("delslave", env, SiteBot.class));
		}
	}
	
	/*
	 * Core.
	 */
	
	/**
	 * Tries to connect, if fails reconnect if hasn't reached the max amount of retries.
	 * @throws Exception 
	 */
	public synchronized void connect() {
		try {			
			_conn.connect();
		} catch (IOException e) {
			logger.info("#" + _connRetry + ": " + e.getMessage(), e);
			logger.info("Tasker will handle reconnection if needed.");
		}
	}
	
	/**
	 * Creates a new IRCConnection object
	 * and then issue connect(),
	 */
	public void createAndConnect() {
		_conn = newIRCConn();
		connect();
	}
	
	/**
	 * Disconnects from the IRC Server and 
	 * set the AutoReconnect to false.
	 * (Tasker will not be able to reconnect)
	 */
	public synchronized void disconnect() {
		if (_conn == null || !isConnected()) 
			return;
		setAutoReconnect(false);
		_conn.close();
	}
	
	/**
	 * Disconnects and let the Tasker handle the reconnection;
	 */
	public void reconnect() {
		disconnect();
		setAutoReconnect(true);
	}
	
	/**
	 * Set the ammount of connection retries to 0;
	 */
	public void resetRetry() {
		_connRetry = 0;
	}
	
	/**
	 * @return the primary channel.
	 * (aka: irc.channel.1=)
	 */
	public String getPrimaryChannel() {
		return _primaryChannelName;
	}
	
	/**
	 * @return the current connection.
	 */
	public IRCConnection getIRCConnection() {
		return _conn;
	}
	
	/**
	 * Changes the current connection to 'conn'.
	 * @param conn
	 */
	public void setIRCConnection(IRCConnection conn) {
		_conn = conn;
	}
	
	/**
	 * @return the SlaveManager.
	 */
	public SlaveManager getSlaveManager() {
		return getGlobalContext().getSlaveManager();
	}
	
	/**
	 * This method is called by GlobalContext to
	 * initialize the plugin. Here we are reading the
	 * configuration files and starting the connection.
	 */
	public void init() {
		try {
			reload();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Main reload method that will call the other ones.
	 * Starts the timer.
	 * @throws IOException
	 */
	public void reload() throws IOException {
		Properties ircCfg = new Properties();
		synchronized(this) {
			FileInputStream fis = new FileInputStream("conf/irc.conf");
			ircCfg.load(fis);
			reloadIRCCommands();
			if (fis != null)
				fis.close();
		}
		reload(ircCfg);
		resetTasker();
		_timer.schedule(_tasker, 30 * 1000, getDelay());
	}
	
	private void resetTasker() {
		_tasker.cancel();
		_timer.purge();
		_tasker = new Tasker(this);
	}
	
	/**
	 * Reloads the irccomands.conf
	 * @throws IOException
	 */
	private void reloadIRCCommands() throws IOException {
		LineNumberReader lineReader = new LineNumberReader(new FileReader("conf/irccommands.conf"));
		
		_ircCommands = new IRCCommands(lineReader);
		
		if (lineReader != null)
			lineReader.close();
	}
	
	/**
	 * Instantiates a new IRCConnection Object
	 * adjusting all settings.
	 * @return
	 */
	public synchronized IRCConnection newIRCConn() {
		logger.debug("Creatin a new IRCConnection instance");
		
		IRCConnection conn;
		
		if (_useSSL) {
			conn = new SSLIRCConnection(_server, _portMin, _portMax, _pass, _nick, _user, _realname);
			logger.info("Attempting to connect using SSL to: " + conn.getHost());
		} else {
			conn = new IRCConnection(_server, _portMin, _portMax, _pass, _nick, _user, _realname);
			logger.info("Attempting to connect to: " + conn.getHost());
		}
		
		// do not edit this part.
		conn.setDaemon(true);
		conn.setColors(false); 
		conn.setPong(true);
		// k, thanks.
		
		Properties ircCfg = _properties;
		
		// load listeners
		for (int i = 1;; i++) {    			
			String classname = ircCfg.getProperty("listener." + i);
			
			if (classname == null) { 
				if (i == 1) 
					logger.debug("No IRCEventListener loaded");
				break;
			}
			
			try {
				Class clazz = Class.forName(classname);
				
				// avoid instantiating the same listener twice, useless.
				IRCEventListener eventListener = _listeners.get(clazz);
				if (eventListener == null) { 
					eventListener = (IRCEventListener) clazz
					.getConstructor(new Class[]{ SiteBot.class })
					.newInstance(new Object[]{ this });
					_listeners.put(clazz, eventListener);
				}
				
				conn.addIRCEventListener(eventListener);
			} catch (Exception e) {
				logger.error(e.getMessage(), e);
				continue;
			}
		}
		
		System.out.println("IRCConnection Instance: " + conn.toString() + "@" + conn.hashCode());
		return conn;
	}
	
	
	public void setIRCConn(IRCConnection conn) {
		_conn = conn;
	}
	
	/**
	 * Loads irc.conf settings, then connect() if needed.
	 */
	private void reload(Properties ircCfg) throws IOException {		
		_properties = ircCfg;
		_server = PropertyHelper.getProperty(ircCfg, "irc.server");
		
		String[] ports = PropertyHelper.getProperty(ircCfg, "irc.port").split("-");
		_portMin = Integer.parseInt(ports[0]);
		try { _portMax = Integer.parseInt(ports[1]); }
		catch (ArrayIndexOutOfBoundsException e) { _portMax = Integer.parseInt(ports[0]); }
		
		_connRetryMax = Long.parseLong(ircCfg.getProperty("irc.retry", "10"));
		_connDelay = Integer.parseInt(ircCfg.getProperty("irc.delay", "10000"));
		
		_pass = ircCfg.getProperty("irc.server.pass", null);		
		_useSSL = ircCfg.getProperty("irc.ssl", "false").equals("true");
		
		_nick = PropertyHelper.getProperty(ircCfg, "irc.nick");
		_user = PropertyHelper.getProperty(ircCfg, "irc.user");
		_realname = PropertyHelper.getProperty(ircCfg, "irc.name");
		
		_psyBNC = ircCfg.getProperty("irc.psybnc.pass", "");
		
		_shutdownEventChannel = ircCfg.getProperty("irc.shutdownevent.channel", "").replace(",", "").trim();
		_inviteEventChannel = ircCfg.getProperty("irc.inviteevent.channel", "").replace(",", "").trim();
		_slaveEventChannel = ircCfg.getProperty("irc.slaveevent.channel", "").replace(",", "").trim();
		
		_perform = ircCfg.getProperty("irc.onconnect.commands", "").trim();
		
		synchronized (this) {
			
			//TODO check!
			// mark that the bot needs to run connect() at the end of reload().
			boolean issueConnect = false;
			// mark that the bot needs to run reconnect() at the end of reload(), before connect(); 
			boolean issueReconnect = false;
			
			boolean portChanged = true;
			boolean identChanged = false;
			
			if (_conn == null) {
				_conn = newIRCConn();
				issueConnect = true;
			} else {
				if (!_conn.getNick().equals(_nick)) {
					logger.info("Switching nick from: " + _conn.getNick() + " to " + _nick);
					_conn.doNick(_nick);
				}
				
				if (!_conn.getUsername().equals(_user))
					identChanged = true;
				
				for (int i = 0; i < _conn.getPorts().length; i++) {
					int port = _conn.getPorts()[i];
					portChanged = (port >= _portMin && port <= _portMax) && portChanged;
				}
				
				if (!_conn.getHost().equals(_server) || !portChanged || identChanged)
					issueReconnect = true;
			}
			
			_enableAnnounce = ircCfg.getProperty("irc.enable.announce", "false").equals("true");
			
			CaseInsensitiveHashMap<String,Channel> oldChannelMap = 
				new CaseInsensitiveHashMap<String,Channel>();
			
			if (_channelMap != null) {
				// it's not the first site reload ;)
				oldChannelMap.putAll(_channelMap);
			}
			
			_channelMap = new CaseInsensitiveHashMap<String,Channel>();
			
			for (int i = 1;; i++) {
				String channelName = ircCfg.getProperty("irc.channel." + i);
				
				if (channelName == null) {
					break;
				}
				
				String blowKey = ircCfg.getProperty("irc.channel." + i + ".blowkey", "");
				String chanKey = ircCfg.getProperty("irc.channel." + i + ".chankey", "");
				String permissions = ircCfg.getProperty("irc.channel." + i + ".perms", "*");
				
				if (i == 1) {
					_primaryChannelName = channelName.toUpperCase();
				}
				
				_channelMap.put(channelName, new Channel(channelName, chanKey, blowKey, permissions));
			}
			
			_sections = new Hashtable<String, SectionSettings>();
			for (int i = 1;; i++) {
				String name = ircCfg.getProperty("irc.section." + i);
				
				if (name == null) {
					break;
				}
				
				String chan = ircCfg.getProperty("irc.section." + i
						+ ".channel");
				
				if (chan == null) {
					chan = _primaryChannelName;
				}
				
				_sections.put(name, new SectionSettings(ircCfg, i, chan));
			}
			
			for (Channel cc : getCurrentChannels(oldChannelMap.values())) {
				String currentChannel = cc.getName();
				
				if (_channelMap.containsKey(currentChannel)) { // still in channel
					Channel oldCC = oldChannelMap.get(currentChannel);
					
					// since we still on the channel, we must keep the chan configuration.
					_channelMap.put(oldCC.getName(), oldCC);
				} else { // removed from channel
					Channel oldCC = oldChannelMap.get(currentChannel);
					_conn.doPart(oldCC.getName(), "Removed from the config file...");
				}
			}
			
			for (String channelName : _channelMap.keySet()) {
				Channel cc = _channelMap.get(channelName);
				if (!cc.isOn()) { 
					// new channel or not an active 'old' channel
					_conn.doJoin(channelName, cc.getKey());
				}
			}
			
			//maximum announcements for race results
			try {
				_maxUserAnnounce = Integer.parseInt(ircCfg.getProperty("irc.max.racers", "100"));
			} catch (NumberFormatException e) {
				logger.warn("Invalid setting in irc.conf: irc.max.racers", e);
				_maxUserAnnounce = 100;
			}
			try {
				_maxGroupAnnounce = Integer.parseInt(ircCfg.getProperty("irc.max.groups", "100"));
			} catch (NumberFormatException e) {
				logger.warn("Invalid setting in irc.conf: irc.max.groups", e);
				_maxGroupAnnounce = 100;
			}
			
			if (issueConnect && !isConnected())
				connect();
			
			if (issueReconnect)
				// let the tasker handle the reconnection.
				reconnect();
		}
	}
	
	/**
	 * Execute commands right after connecting.
	 */
	public void perform() {
		StringTokenizer st = new StringTokenizer(_perform, "|");
		while (st.hasMoreTokens())
			_conn.send(st.nextToken().trim());
	}
	/**
	 * Returns the value of 'irc.delay'
	 */
	public int getDelay() {
		return _connDelay;
	}
	
	/**
	 * Returns the number of current retries.
	 */
	public long getRetries() {
		return _connRetry;
	}
	
	/**
	 * Changes the amount of retries.
	 * @param amount
	 */
	public void setRetries(long amount) {
		_connRetry = amount;
	}
	
	/**
	 * The max amount of retries.
	 */
	public long getMaxNumRetries() {
		return _connRetryMax;
	}
	
	/**
	 * @return psyBNC pass
	 */
	public String getPsyBNC() {
		return _psyBNC;
	}
	
	/**
	 * Return the channel Map.
	 */
	public CaseInsensitiveHashMap<String, Channel> getChannelMap() {
		return _channelMap;
	}
	
	/**
	 * Return all channels loaded from irc.conf
	 * @return Channel Collection
	 */
	public Collection<Channel> getChannels() {
		return _channelMap.values();
	}
	
	/**
	 * @param chan
	 * @return The channel object of a given string.
	 */
	public Channel getChannelByName(String chan) {
		return _channelMap.get(chan);
	}
	
	/**
	 * @return Current joined channels
	 */
	public Collection<Channel> getCurrentChannels() {
		return getCurrentChannels(getChannels());
	}
	
	/**
	 * Takes an Collection as parameter and filter the Collection
	 * keeping only the channels that the SiteBot is currently on.
	 * @param chans
	 * @return 
	 */
	public Collection<Channel> getCurrentChannels(Collection<Channel> chans) {
		ArrayList<Channel> list = new ArrayList<Channel>();
		for (Channel c : chans) {
			if (c.isOn()) 
				list.add(c);
		}		
		return list;
	}
	
	/**
	 * @param target
	 * @return true if it is a private message or false if it is not
	 */
	public static boolean isPrivate(String target) {
		return !IRCUtil.isChan(target);
	}
	
	/**
	 * @return the Map of irc commands triggers/methods
	 */
	public TreeMap<String, CommandList> getMethodMap() {
		return _ircCommands.getMethodMap();
	}
	
	public Vector<WhoisEntry> getIdentWhoisList() {
		return _identWhoisList;
	}	
	
	/**
	 * @return true, if the sitebot is connected
	 * false, if disconnected.
	 */
	public synchronized boolean isConnected() {
		return _conn.isConnected();
	}
	
	public boolean autoReconnect() {
		return _autoReconnect;
	}
	
	public void setAutoReconnect(boolean autoReconnect) {
		_autoReconnect = autoReconnect;
	}
	
	public void say(SectionInterface section, String message) {
		SectionSettings sn = null;
		
		if (section != null) {
			sn = (SectionSettings) _sections.get(section.getName());
		}
		say((sn != null) ? sn.getChannel() : _primaryChannelName, message);
	}
	
	public void say(String message) {
		say(_primaryChannelName, message);
	}
	
	
	public void sayGlobal(String string) {
		for (Channel c : getCurrentChannels())
			say(c.getName(), string);
	}
	
	public synchronized void say(String dest, String message) {
		if (message == null || message.equals("")) {
			return;
		}
		boolean isChan = !isPrivate(dest);
		String[] lines = message.split("\n");
		if (isChan) {
			Channel cc = _channelMap.get(dest);
			if (cc == null) {
				logger.error("This is a bug! report me! -- channel=" + dest + " ccMap=" + _channelMap, new Throwable());
				return;
			}
			for (String line : lines) {
				line = cc.encrypt(line);
				_conn.doPrivmsg(dest, line);
			}
		} else {
			for (String line : lines) {
				_conn.doPrivmsg(dest, line);
			}
		}
	}
	
	public synchronized void notice(String dest, String message) {
		if (message == null || message.equals("")) {
			return;
		}
		boolean isChan = isPrivate(dest);
		String[] lines = message.split("\n");
		for (String line : lines) {
			if (isChan) {
				Channel cc = _channelMap.get(dest);
				if (cc == null) {
					logger.error("This is a bug! report me! -- channel=" + dest + " cc=" + cc, new Throwable());
					continue;
				}
				line = cc.encrypt(line);
			}
			_conn.doNotice(dest,line);
		}
	}
	
	private void sayDirectorySection(DirectoryFtpEvent direvent, String string)
	throws FormatterException {
		Ret ret = getPropertyFileSuffix(string, direvent.getDirectory());
		String format = ret.getFormat();
		
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		fillEnvSection(env, direvent, ret.getSection());
		
		say(ret.getSection(), SimplePrintf.jprintf(format, env));
	}
	
	// why the hell is this here? don't we already have 10 methods that do this?
	private String strippath(String path) {
		if (!path.startsWith("/")) {
			return path;
		}
		return path.substring(1);
	}
	
	public synchronized void unload() {
		disconnect();
	}
	
	/**
	 * Returns the blowfish key for the channel specified
	 */
	public synchronized String getBlowfishKey(String channel, User user) throws ObjectNotFoundException {
		Channel cc = _channelMap.get(channel);
		if (cc != null) {
			return cc.getBlowfishKey(user);
		}
		throw new ObjectNotFoundException();
	}
	/**
	 * Returns the blowfish key for the primary channel
	 */
	public String getBlowfishKey(User user) throws ObjectNotFoundException {
		return getBlowfishKey(_primaryChannelName, user);
	}
	
	private void fillEnvSection(ReplacerEnvironment env,
			DirectoryFtpEvent direvent, SectionInterface section) {
		fillEnvSection(env, direvent, section, direvent.getDirectory());
	}
	
	private void fillEnvSection(ReplacerEnvironment env,
			DirectoryFtpEvent direvent, SectionInterface section,
			LinkedRemoteFileInterface file) {
		env.add("user", direvent.getUser().getName());
		env.add("group", direvent.getUser().getGroup());
		env.add("section", section.getName());
		
		LinkedRemoteFileInterface dir = file;
		
		if (dir.isFile()) {
			dir = dir.getParentFileNull();
		}
		
		long starttime;
		
		try {
			starttime = FileUtils.getOldestFile(dir).lastModified();
		} catch (ObjectNotFoundException e) {
			starttime = dir.lastModified();
		}

		env.add("size", Bytes.formatBytes(file.getSize()));
		env.add("path",
				strippath(dir.getPath().substring(section.getPath().getSize())));
		env.add("file", file.getName());
		
		if (file.isFile()) {
			env.add("speed",
					Bytes.formatBytes(file.getXferspeed() * 1000) + "/s");
			file = file.getParentFileNull(); // files always have parent dirs.
		}
		
		long elapsed = (direvent.getTime() - starttime);
		
		env.add("secondstocomplete",
				Time.formatTime(elapsed));
		
		long elapsedSeconds = elapsed / 1000;
		env.add("averagespeed",
				(elapsedSeconds == 0) ? "n/a"
						: (Bytes.formatBytes(
								dir.getSize() / elapsedSeconds) + "/s"));

		SFVFile sfvfile;
		
		try {
			sfvfile = file.lookupSFVFile();

			env.add("totalfiles", "" + sfvfile.size());
			env.add("totalspeed", Bytes.formatBytes(sfvfile.getXferspeed()));
		} catch (Exception ex) {
			env.add("totalfiles", "" + file.getDirectories().size());
			//COULD BE multi-cd, PRE will have to get it owns fillEnvSection with sub-dir .sfv support!
			if (ex instanceof FileNotFoundException) {
				// no need to spam FileNotFound on SFVFile lookups
				return;
			}
			logger.warn("Couldn't get SFV file in announce");
		}
	}
	
	
	public static void fillEnvSlaveStatus(ReplacerEnvironment env,
			SlaveStatus status, SlaveManager slaveManager) {
		env.add("disktotal", Bytes.formatBytes(status.getDiskSpaceCapacity()));
		env.add("diskfree", Bytes.formatBytes(status.getDiskSpaceAvailable()));
		env.add("diskused", Bytes.formatBytes(status.getDiskSpaceUsed()));
		
		if (status.getDiskSpaceCapacity() == 0) {
			env.add("diskfreepercent", "n/a");
			env.add("diskusedpercent", "n/a");
		} else {
			env.add("diskfreepercent",
					((status.getDiskSpaceAvailable() * 100) / status.getDiskSpaceCapacity()) +
			"%");
			env.add("diskusedpercent",
					((status.getDiskSpaceUsed() * 100) / status.getDiskSpaceCapacity()) +
			"%");
		}
		
		env.add("xfers", "" + status.getTransfers());
		env.add("xfersdn", "" + status.getTransfersSending());
		env.add("xfersup", "" + status.getTransfersReceiving());
		env.add("xfersdown", "" + status.getTransfersSending());
		
		env.add("throughput", Bytes.formatBytes(status.getThroughput()) + "/s");
		
		env.add("throughputup",
				Bytes.formatBytes(status.getThroughputReceiving()) + "/s");
		
		env.add("throughputdown",
				Bytes.formatBytes(status.getThroughputSending()) + "/s");
		
		try {
			env.add("slaves", "" + slaveManager.getAvailableSlaves().size());
		} catch (NoAvailableSlaveException e2) {
			env.add("slaves", "0");
		}
	}
	
	/*
	 * Misc stuff.
	 */	
	
	/**
	 * All parameters are very nescessary, each of them have their own very important
	 * hole on the code, adding 'null' values to them will prolly cause some annoying
	 * NullPointerExceptions.<br>
	 * Returns the User object, if the user exists, or 'null' if the user doesn't exists.<br>
	 * Sample usage:<br> <pre>
	 * ArrayList<String> output = new ArrayList<String>();
	 * ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);
	 * User user = SiteBot.getUserByNickname(msgc.getSource(), out, env, logger);
	 * if (user == null)
	 * 	return out;
	 * <pre>            
	 * @param ircUser, Nick!Ident@Hostmask
	 * @param output, reference to the ArrayList object used on the IRCCommand.
	 * @param env, reference to the ReplacerEnvironment object used on the IRCCommand.
	 * @param log, current logger for that class, will provide accurate logger output.
	 * @return the User object if the user exists, or 'null' if the user doesn't exists.
	 */
	public static User getUserByNickname(IRCUser ircUser, ArrayList<String> output, 
			ReplacerEnvironment env, Logger log) {
		String nih = ircUser.getNick() + "!" + ircUser.getUsername() + "@" + ircUser.getHost();
		User user;
		try {
			user = getGlobalContext().getUserManager().getUserByIdent(nih);
			env.add("ftpuser",user.getName());
		} catch (Exception e) {
			env.add("reason", e.getMessage());
			//TODO generic message
			output.add(ReplacerUtils.jprintf("ident.noident", env, SiteBot.class));
			log.warn("Could not identify " + 
					nih.substring(0,nih.indexOf("!")-1)
					+ ": " + e.getMessage(), e);
			return null;
		}
		return user;
	}
	
	public Ret getPropertyFileSuffix(String prefix,
			LinkedRemoteFileInterface dir) {
		SectionInterface sectionObj = getGlobalContext()
		.getSectionManager().lookup(dir.getPath());
		
		try {
			return new Ret(ResourceBundle.getBundle(SiteBot.class.getName())
					.getString(prefix), sectionObj);
		} catch (MissingResourceException e) {
			logger.warn(e, e);
		}
		return new Ret("", sectionObj); 
	}
	
	/**
	 * WhoisEntries.
	 * Used to identify the users which were invited through 'site invite'.
	 */
	public class WhoisEntry {
		private long _created = System.currentTimeMillis();
		private String _nick = null;
		private User _user = null;
		
		public WhoisEntry(String nick, User user) {
			_nick = nick;
			_user = user;
		}
		
		public String getNick() {
			return _nick;
		}
		
		public User getUser() {
			return _user;
		}
		
		public long getCreated() {
			return _created;
		}
	}
	
	public class Ret {
		private String _format;
		private SectionInterface _section;
		
		public Ret(String string, SectionInterface sectionObj) {
			_format = string;
			_section = sectionObj;
		}
		
		public String getFormat() {
			return _format;
		}
		
		public SectionInterface getSection() {
			return _section;
		}
	}
	
	/**
	 * Section Settings are used to spam a section on
	 * a different channel.
	 */
	public class SectionSettings {
		private String _channel;
		private ReplacerEnvironment _env = new ReplacerEnvironment();
		
		public SectionSettings(Properties p, int i) {}
		
		public SectionSettings(Properties ircCfg, int i, String channel) {
			_channel = channel;
		}
		
		public String getChannel() {
			return _channel;
		}
		
		public ReplacerEnvironment getEnv() {
			return _env;
		}
	}
}