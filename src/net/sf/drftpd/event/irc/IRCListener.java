package net.sf.drftpd.event.irc;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;
import java.util.ResourceBundle;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.SFVFile.SFVStatus;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.UploaderPosition;
import net.sf.drftpd.master.GroupPosition;
import net.sf.drftpd.master.command.plugins.Nuke;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;
import net.sf.drftpd.util.ReplacerUtils;
import net.sf.drftpd.util.Time;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.tanesha.replacer.FormatterException;
import org.tanesha.replacer.ReplacerEnvironment;
import org.tanesha.replacer.ReplacerFormat;
import org.tanesha.replacer.SimplePrintf;

import f00f.net.irc.martyr.AutoJoin;
import f00f.net.irc.martyr.AutoReconnect;
import f00f.net.irc.martyr.AutoRegister;
import f00f.net.irc.martyr.AutoResponder;
import f00f.net.irc.martyr.Debug;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.clientstate.ClientState;
import f00f.net.irc.martyr.commands.InviteCommand;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.NickCommand;
import f00f.net.irc.martyr.commands.PartCommand;

/**
 * @author mog
 * @version $Id: IRCListener.java,v 1.80 2004/02/04 21:16:57 mog Exp $
 */
public class IRCListener implements FtpListener, Observer {

	public static class Ret {
		public String _format;
		public LinkedRemoteFile _section;
		public Ret(String format, LinkedRemoteFile dir) {
			_format = format;
			_section = dir;
		}
	}
	private AutoJoin _autoJoin;
	private AutoRegister _autoRegister;
	private static final Logger logger = Logger.getLogger(IRCListener.class);

	/**
	 * Used from FtpConnection
	 * @param files Collection of LinkedRemoteFile objects
	 */
	public static Collection topFileUploaders(Collection files) {
		ArrayList ret = new ArrayList();
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String username = file.getUsername();

			UploaderPosition stat = null;
			for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
				UploaderPosition stat2 = (UploaderPosition) iter2.next();
				if (stat2.getUsername().equals(username)) {
					stat = stat2;
					break;
				}
			}
			if (stat == null) {
				stat =
					new UploaderPosition(
						username,
						file.length(),
						1,
						file.getXfertime());
				ret.add(stat);
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
				stat.updateXfertime(file.getXfertime());
			}
		}
		Collections.sort(ret);
		return ret;
	}
	
	public static Collection topFileGroup(Collection files) {
		ArrayList ret = new ArrayList();
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String groupname = file.getGroupname();

			GroupPosition stat = null;
			for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
				GroupPosition stat2 = (GroupPosition) iter2.next();
				if (stat2.getGroupname().equals(groupname)) {
					stat = stat2;
					break;
				}
			}
			if (stat == null) {
				stat =
					new GroupPosition(
						groupname,
						file.length(),
						1,
						file.getXfertime());
				ret.add(stat);
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
				stat.updateXfertime(file.getXfertime());
			}
		}
		Collections.sort(ret);
		return ret;
	}

	private AutoReconnect _autoReconnect;
	private String _channelName;

	private ClientState _clientState;
	private ConnectionManager _cm;

	private IRCConnection _conn;

	private String _key;
	private int _port;

	private String _server;
	public static final ReplacerEnvironment GLOBAL_ENV =
		new ReplacerEnvironment();
	static {
		GLOBAL_ENV.add("bold", "\u0002");
		GLOBAL_ENV.add("coloroff", "\u000f");
		GLOBAL_ENV.add("color", "\u0003");
	}

	public IRCListener() throws UnknownHostException, IOException {

		new File("logs").mkdirs();
		Debug.setOutputStream(
			new PrintStream(new FileOutputStream("logs/sitebot.log")));
		Debug.setDebugLevel(Debug.FAULT);
	}

	public void actionPerformed(Event event) {
		try {
			if (event instanceof DirectoryFtpEvent) {
				actionPerformedDirectory((DirectoryFtpEvent) event);
			} else if (event instanceof NukeEvent) {
				actionPerformedNuke((NukeEvent) event);
			} else if (event instanceof SlaveEvent) {
				actionPerformedSlave((SlaveEvent) event);
			} else if (event instanceof InviteEvent) {
				actionPerformedInvite((InviteEvent) event);
			} else if (event.getCommand().equals("RELOAD")) {
				try {
					reload();
				} catch (IOException e) {
					logger.log(Level.WARN, "", e);
				}

			} else if (event.getCommand().equals("SHUTDOWN")) {
				MessageEvent mevent = (MessageEvent) event;
				ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
				env.add("message", mevent.getMessage());

				say(ReplacerUtils.jprintf("shutdown", env, IRCListener.class));
			}
		} catch (FormatterException ex) {
			say(event.getCommand() + " FormatterException: " + ex.getMessage());
			logger.warn("", ex);
		}
	}

	private void actionPerformedDirectory(DirectoryFtpEvent direvent)
		throws FormatterException {
		if (!getConfig()
			.checkDirLog(direvent.getUser(), direvent.getDirectory())) {
			return;
		}
		if ("MKD".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "mkdir");
		} else if ("REQUEST".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "request");
		} else if ("REQFILLED".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "reqfilled");
		} else if ("RMD".equals(direvent.getCommand())) {
			sayDirectorySection(direvent, "rmdir");
		} else if ("WIPE".equals(direvent.getCommand())) {
			if (direvent.getDirectory().isDirectory()) {
				sayDirectorySection(direvent, "wipe");
			}
		} else if ("PRE".equals(direvent.getCommand())) {

			Ret obj = getPropertyFileSuffix("pre", direvent.getDirectory());
			String format = obj._format;
			LinkedRemoteFile dir = obj._section;

			ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
			fillEnvSection(env, direvent, dir);

			say(SimplePrintf.jprintf(format, env));

		} else if (direvent.getCommand().equals("STOR")) {
			actionPerformedDirectorySTOR(direvent);
		}
	}

	private void actionPerformedDirectorySTOR(DirectoryFtpEvent direvent)
		throws FormatterException {
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		LinkedRemoteFile dir;
		try {
			dir = direvent.getDirectory().getParentFile();
		} catch (FileNotFoundException e) {
			throw new FatalException(e);
		}
		SFVFile sfvfile;
		try {
			sfvfile = dir.lookupSFVFile();
			// throws IOException, ObjectNotFoundException, NoAvailableSlaveException
		} catch (FileNotFoundException ex) {
			logger.info(
				"No sfv file in "
					+ direvent.getDirectory().getPath()
					+ ", can't publish race info");
			return;
		} catch (NoAvailableSlaveException e) {
			logger.info("No available slave with .sfv");
			return;
		} catch (IOException e) {
			logger.warn("IO error reading .sfv", e);
			return;
		}

		long starttime = Long.MAX_VALUE;
		for (Iterator iter = sfvfile.getFiles().iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			if (file.lastModified() < starttime)
				starttime = file.lastModified();
		}

		if (!sfvfile.hasFile(direvent.getDirectory().getName()))
			return;

		int halfway = (int) Math.floor((double) sfvfile.size() / 2);
		///// start ///// start ////

		//check if new racer
		String username = direvent.getUser().getUsername();
		SFVStatus sfvstatus = sfvfile.getStatus();
		if (sfvfile.size() - sfvstatus.getMissing() != 1) {
			for (Iterator iter = sfvfile.getFiles().iterator();
				iter.hasNext();
				) {
				LinkedRemoteFile sfvFileEntry = (LinkedRemoteFile) iter.next();
				if (sfvFileEntry == direvent.getDirectory())
					continue;
				if (sfvFileEntry.getUsername().equals(username))
					break;
				if (!iter.hasNext()) {

					Ret obj =
						getPropertyFileSuffix(
							"store.embraces",
							direvent.getDirectory());
					String format = obj._format;
					LinkedRemoteFile section = obj._section;

					//					ReplacerEnvironment env =
					//						new ReplacerEnvironment(globalEnv);
					fillEnvSection(
						env,
						direvent,
						section,
						direvent.getDirectory());
					env.add(
						"filesleft",
						Integer.toString(sfvstatus.getMissing()));

					say(SimplePrintf.jprintf(format, env));
				}
			}
		}

		//COMPLETE
		if (sfvstatus.isFinished()) {
			Collection racers = topFileUploaders(sfvfile.getFiles());
			Collection groups = topFileGroup(sfvfile.getFiles());
			Ret ret = getPropertyFileSuffix("store.complete", dir);
			String format = ret._format;
			LinkedRemoteFile section = ret._section;

			try {
				fillEnvSection(
					env,
					direvent,
					section,
					direvent.getDirectory().getParentFile());
			} catch (FileNotFoundException e6) {
				logger.log(Level.FATAL, "", e6);
			}
			env.add("racers", Integer.toString(racers.size()));
			env.add("groups", Integer.toString(groups.size()));
			env.add("files", Integer.toString(sfvfile.size()));
			env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()));
			env.add("speed", Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
			env.add(
				"secondstocomplete",
				Time.formatTime((direvent.getTime() - starttime)));
			say(SimplePrintf.jprintf(format, env));

			Ret ret2 = getPropertyFileSuffix("store.complete.racer", dir);
			ReplacerFormat raceformat;
			// already have section from ret.section
			raceformat = ReplacerFormat.createFormat(ret2._format);

			int position = 1;
			for (Iterator iter = racers.iterator(); iter.hasNext();) {
				UploaderPosition stat = (UploaderPosition) iter.next();

				User raceuser;
				try {
					raceuser =
						_cm.getUserManager().getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(GLOBAL_ENV);

				raceenv.add("user", raceuser.getUsername());
				raceenv.add("group", raceuser.getGroupName());

				raceenv.add("position", new Integer(position++));
				raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("files", Integer.toString(stat.getFiles()));
				raceenv.add(
					"percent",
					Integer.toString(stat.getFiles() * 100 / sfvfile.size())
						+ "%");
				raceenv.add(
					"speed",
					Bytes.formatBytes(stat.getXferspeed()) + "/s");

				say(SimplePrintf.jprintf(raceformat, raceenv));
			}
			
			Ret ret3 = getPropertyFileSuffix("store.complete.group", dir);
			// already have section from ret.section
			raceformat = ReplacerFormat.createFormat(ret3._format);
			say(
				SimplePrintf.jprintf(
					getPropertyFileSuffix("store.complete.group.header",dir)._format,
					env));
			
			position = 1;
			for (Iterator iter = groups.iterator(); iter.hasNext();) {
				GroupPosition stat = (GroupPosition) iter.next();

				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(GLOBAL_ENV);

				raceenv.add("group", stat.getGroupname());

				raceenv.add("position", new Integer(position++));
				raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
				raceenv.add("files", Integer.toString(stat.getFiles()));
				raceenv.add(
					"percent",
					Integer.toString(stat.getFiles() * 100 / sfvfile.size())
						 + "%");
				raceenv.add(
					"speed",
					Bytes.formatBytes(stat.getXferspeed()) + "/s");

				say(SimplePrintf.jprintf(raceformat, raceenv));
			}			
 			//HALFWAY
		} else if (sfvfile.size() >= 4 && sfvstatus.getMissing() == halfway) {
			Collection uploaders = topFileUploaders(sfvfile.getFiles());
			//			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			UploaderPosition stat =
				(UploaderPosition) uploaders.iterator().next();

			env.add("leadspeed", Bytes.formatBytes(stat.getXferspeed()) + "/s");
			env.add("leadfiles", Integer.toString(stat.getFiles()));
			env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
			env.add(
				"leadpercent",
				Integer.toString(stat.getFiles() * 100 / sfvfile.size()) + "%");
			env.add("filesleft", Integer.toString(sfvstatus.getMissing()));
			User leaduser;
			try {
				leaduser =
					_cm.getUserManager().getUserByName(stat.getUsername());
				env.add("leaduser", leaduser.getUsername());
				env.add("leadgroup", leaduser.getGroupName());
			} catch (NoSuchUserException e3) {
				logger.log(Level.WARN, "", e3);
			} catch (UserFileException e3) {
				logger.log(Level.WARN, "", e3);
			}

			Ret obj = getPropertyFileSuffix("store.halfway", dir);
			String format = (String) obj._format;
			LinkedRemoteFile section = obj._section;

			fillEnvSection(env, direvent, section);

			say(SimplePrintf.jprintf(format, env));

			//					for (Iterator iter =
			//						topFileUploaders2(sfvfile.getFiles()).iterator();
			//						iter.hasNext();
			//						) {
			//						UploaderPosition stat = (UploaderPosition) iter.next();
			//						String str1;
			//						try {
			//							str1 =
			//								formatUser(
			//									_cm.getUsermanager().getUserByName(
			//										stat.getUsername()));
			//						} catch (NoSuchUserException e2) {
			//							continue;
			//						} catch (IOException e2) {
			//							logger.log(
			//								Level.FATAL,
			//								"Error reading userfile",
			//								e2);
			//							continue;
			//						}
			//						say(
			//							str1
			//								+ " ["
			//								+ stat.getFiles()
			//								+ "f/"
			//								+ Bytes.formatBytes(stat.getBytes())
			//								+ "]");
			//					}
		}

	}

	private void actionPerformedNuke(NukeEvent event)
		throws FormatterException {
		String cmd = event.getCommand();
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		env.add("size", Bytes.formatBytes(event.getSize()));
		//TODO nuke section, we don't have a LinkedRemoteFile :(
		//env.add("section", )

		//Ret ret = getPropertyFileSuffix("nuke", event.getPath());
		env.add("path", event.getPath());
		env.add("reason", event.getReason());
		env.add("multiplier", String.valueOf(event.getMultiplier()));

		env.add("user", event.getUser().getUsername());
		env.add("group", event.getUser().getGroupName());

		//env.add("nukees", event.getNukees().keySet());

		if (cmd.equals("NUKE")) {
			say(
				ReplacerUtils.jprintf(
					"nuke",
					env,
					IRCListener.class.getName()));

			ReplacerFormat raceformat =
				ReplacerUtils.finalFormat(IRCListener.class, "nuke.nukees");

			int position = 1;
			long nobodyAmount = 0;
			for (Iterator iter = event.getNukees2().iterator();
				iter.hasNext();
				) {
				Nukee stat = (Nukee) iter.next();

				User raceuser;
				try {
					raceuser =
						_cm.getUserManager().getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					nobodyAmount += stat.getAmount();
					continue;
				} catch (UserFileException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(GLOBAL_ENV);

				raceenv.add("user", raceuser.getUsername());
				raceenv.add("group", raceuser.getGroupName());

				raceenv.add("position", "" + position++);
				raceenv.add("size", Bytes.formatBytes(stat.getAmount()));

				long nukedamount =
					Nuke.calculateNukedAmount(
						stat.getAmount(),
						raceuser.getRatio(),
						event.getMultiplier());
				raceenv.add("nukedamount", Bytes.formatBytes(nukedamount));
				say(SimplePrintf.jprintf(raceformat, raceenv));

			}
			if (nobodyAmount != 0) {
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(GLOBAL_ENV);

				raceenv.add("user", "nobody");
				raceenv.add("group", "nogroup");

				raceenv.add("position", "?");
				raceenv.add("size", Bytes.formatBytes(nobodyAmount));

				say(SimplePrintf.jprintf(raceformat, raceenv));

			}
		} else if (cmd.equals("UNNUKE")) {

			say(ReplacerUtils.jprintf("unnuke", env, IRCListener.class));
		}
	}

	public static ArrayList map2nukees(Map nukees) {
		ArrayList ret = new ArrayList();
		for (Iterator iter = nukees.entrySet().iterator(); iter.hasNext();) {
			Map.Entry element = (Map.Entry) iter.next();
			ret.add(
				new Nukee(
					(String) element.getKey(),
					((Long) element.getValue()).longValue()));
		}
		Collections.sort(ret);
		return ret;
	}

	private void actionPerformedSlave(SlaveEvent event)
		throws FormatterException {
		SlaveEvent sevent = (SlaveEvent) event;
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		env.add("slave", sevent.getRSlave().getName());
		env.add("message", sevent.getMessage());
		if (event.getCommand().equals("ADDSLAVE")) {
			SlaveStatus status;
			try {
				status = sevent.getRSlave().getStatus();
			} catch (RemoteException e) {
				sevent.getRSlave().handleRemoteException(e);
				return;
			} catch (NoAvailableSlaveException e) {
				return;
			}
			fillEnvSpace(env, status);

			say(ReplacerUtils.jprintf("addslave", env, IRCListener.class));
		} else if (event.getCommand().equals("DELSLAVE")) {
			say(ReplacerUtils.jprintf("delslave", env, IRCListener.class));
		}
	}

	private void actionPerformedInvite(InviteEvent event) {
		String user = event.getUser();
		logger.info("Invited " + user + " through SITE INVITE");
		_conn.sendCommand(new InviteCommand(user, _channelName));
	}

	private void fillEnvSection(
		ReplacerEnvironment env,
		DirectoryFtpEvent direvent,
		LinkedRemoteFile section) {
		fillEnvSection(env, direvent, section, direvent.getDirectory());
	}

	private void fillEnvSection(
		ReplacerEnvironment env,
		DirectoryFtpEvent direvent,
		LinkedRemoteFile section,
		LinkedRemoteFile file) {
		env.add("user", direvent.getUser().getUsername());
		env.add("group", direvent.getUser().getGroupName());
		env.add("section", strippath(section.getPath()));

		LinkedRemoteFile dir = file;
		if(dir.isFile()) dir = dir.getParentFileNull();
		
	long elapsed;
		try {
			elapsed = dir.getOldestFile().lastModified()/1000;
		} catch (ObjectNotFoundException e) {
			elapsed = dir.lastModified()/1000;
		}
		elapsed = System.currentTimeMillis() - elapsed;
		env.add("elapsed", ""+elapsed);
		env.add("elapsed", ""+elapsed);
		env.add("averagespeed", Bytes.formatBytes(dir.dirSize()/elapsed/1000)+"/s");

		if (file.isFile()) {
			env.add("size", Bytes.formatBytes(file.length()));
			env.add("speed", Bytes.formatBytes(file.getXferspeed()) + "/s");
		} else if (file.isDirectory()) {
			SFVFile sfvfile;
			try {
				sfvfile = file.lookupSFVFile();
				//env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()()));
				env.add("totalfiles", "" + sfvfile.size());
				env.add(
					"totalspeed",
					Bytes.formatBytes(sfvfile.getXferspeed()));
			} catch (Exception ex) {
				//COULD BE multi-cd, PRE will have to get it owns fillEnvSection with sub-dir .sfv support!
				logger.warn("Couldn't get SFV file in announce", ex);
				//env.add("size", Bytes.formatBytes(file.length()));
				env.add("totalfiles", "" + file.getFiles().size());
			}
		} else {
			throw new Error("Not a file or directory, what weird shit are we then?");
		}

		env.add(
			"path",
			strippath(dir.getPath().substring(section.getPath().length())));
		env.add("file", file.getName());
	}

	void fillEnvSpace(ReplacerEnvironment env, SlaveStatus status) {
		env.add("xfers", Integer.toString(status.getTransfers()));
		env.add("throughput", Bytes.formatBytes(status.getThroughput()));

		env.add("xfersup", Integer.toString(status.getTransfersReceiving()));
		env.add(
			"throughputup",
			Bytes.formatBytes(status.getThroughputReceiving()));

		env.add("xfersdn", Integer.toString(status.getTransfersSending()));
		env.add(
			"throughputdown",
			Bytes.formatBytes(status.getThroughputSending()));

		env.add("disktotal", Bytes.formatBytes(status.getDiskSpaceCapacity()));
		env.add("diskfree", Bytes.formatBytes(status.getDiskSpaceAvailable()));
		env.add("diskused", Bytes.formatBytes(status.getDiskSpaceUsed()));
		try {
			env.add(
				"slaves",
				"" + getSlaveManager().getAvailableSlaves().size());
		} catch (NoAvailableSlaveException e) {
			env.add("slaves", "0");
		}
	}

	public FtpConfig getConfig() {
		return _cm.getConfig();
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}
	public IRCConnection getIRCConnection() {
		return _conn;
	}

	public Ret getPropertyFileSuffix(String prefix, LinkedRemoteFile dir) {
		LinkedRemoteFile section = null;
		LinkedRemoteFile tmp2 = dir, tmp1 = dir;
		try {
			while (true) {
				section = tmp2;
				tmp2 = tmp1;
				tmp1 = tmp1.getParentFile();
			}
		} catch (FileNotFoundException success) {
		}
		return new Ret(
			ResourceBundle.getBundle(IRCListener.class.getName()).getString(
				prefix),
			section);
	}

	public SlaveManagerImpl getSlaveManager() {
		return getConnectionManager().getSlaveManager();
	}

	private void reload() throws FileNotFoundException, IOException {
		Properties ircCfg = new Properties();
		ircCfg.load(new FileInputStream("conf/irc.conf"));
		_server = FtpConfig.getProperty(ircCfg, "irc.server");
		_port = Integer.parseInt(FtpConfig.getProperty(ircCfg, "irc.port"));
		String oldchannel = _channelName;
		_channelName = FtpConfig.getProperty(ircCfg, "irc.channel");
		_key = ircCfg.getProperty("irc.key");
		if (_key.equals(""))
			_key = null;
		if (_conn == null
			|| !_conn.getClientState().getServer().equals(_server)
			|| _conn.getClientState().getPort() != _port) {
			logger.info("Reconnecting due to server change");
			connect(ircCfg);
		} else {
			if (!_conn
				.getClientState()
				.getNick()
				.getNick()
				.equals(FtpConfig.getProperty(ircCfg, "irc.nick"))) {
				logger.info("Switching to new nick");
				_autoRegister.disable();
				_autoRegister = addAutoRegister(ircCfg);
				_conn.sendCommand(
					new NickCommand(ircCfg.getProperty("irc.nick")));
			}
			if (!_conn.getClientState().isOnChannel(_channelName)) {
				_autoJoin.disable();
				_conn.removeCommandObserver(_autoJoin);
				_conn.sendCommand(new PartCommand(oldchannel));
				_autoJoin = new AutoJoin(_conn, _channelName, _key);
			}
		}
	}
	private AutoRegister addAutoRegister(Properties ircCfg) {
		return new AutoRegister(
			_conn,
			FtpConfig.getProperty(ircCfg, "irc.nick"),
			FtpConfig.getProperty(ircCfg, "irc.user"),
			FtpConfig.getProperty(ircCfg, "irc.name"));
	}
	private void connect(Properties ircCfg)
		throws UnknownHostException, IOException {
		if (_conn != null) {
			_autoReconnect.disable();
			_conn.disconnect();
		}
		_clientState = new ClientState();
		_conn = new IRCConnection(_clientState);

		_autoReconnect = new AutoReconnect(_conn);
		_autoRegister = addAutoRegister(ircCfg);
		_autoJoin = new AutoJoin(_conn, _channelName, _key);
		new AutoResponder(_conn);
		_conn.addCommandObserver(this);

		for (int i = 1;; i++) {
			String classname = ircCfg.getProperty("irc.plugins." + i);
			if (classname == null)
				break;
			Observer obs;
			try {
				logger.debug("Loading " + Class.forName(classname));
				obs =
					(Observer) Class
						.forName(classname)
						.getConstructor(new Class[] { IRCListener.class })
						.newInstance(new Object[] { this });
				_conn.addCommandObserver(obs);
			} catch (Exception e) {
				logger.warn("", e);
				throw new RuntimeException(
					"Error loading IRC plugin :" + classname,
					e);
			}
		}
		connect();
	}

	public void disconnect() {
		_autoReconnect.disable();
		_conn.disconnect();
	}
	
	public void connect() throws UnknownHostException, IOException {
		logger.info("IRCListener: connecting to " + _server + ":" + _port);
		_conn.connect(_server, _port);
	}
	public void say(String message) {
		//		if (!_clientState.isOnChannel(_channelName)) {
		//			logger.warn("Not in "+_channelName+", dropping message");
		//			return;
		//		}
		if (message.equals(""))
			throw new IllegalArgumentException("Cowardly refusing to send empty message");

		String lines[] = message.split("\n");
		for (int i = 0; i < lines.length; i++) {
			String line = lines[i];
			String chan;
			if (line.startsWith("#")) {
				int pos = line.indexOf(':');
				chan = line.substring(0, pos);
				line = line.substring(pos + 1);
			} else {
				chan = _channelName;
			}
			_conn.sendCommand(new MessageCommand(chan, line));
		}
	}

	private void sayDirectorySection(DirectoryFtpEvent direvent, String string)
		throws FormatterException {

		Ret obj = getPropertyFileSuffix(string, direvent.getDirectory());
		String format = obj._format;
		LinkedRemoteFile section = obj._section;

		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		fillEnvSection(env, direvent, section);

		say(SimplePrintf.jprintf(format, env));
	}

	public String strippath(String path) {
		if (!path.startsWith("/")) {
			logger.debug(
				path + " didn't start with /, unneeded call to strippath()",
				new Throwable());
			return path;
		}
		return path.substring(1);
	}

	public void update(Observable observer, Object updated) {
		try {
			if (updated instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) updated;
				String msg = msgc.getMessage();
				//only accept messages from _channelName
				if (!msgc.getDest().equalsIgnoreCase(_channelName))
					return;

				if (msg.equals("!help")) {
					say("Available commands: !bw !slaves !speed !who");
				} else if (msg.equals("!bw")) {
					try {
						updateBw(observer, msgc);
					} catch (FormatterException e) {
						say("[bw] FormatterException: " + e.getMessage());
					}
				} else if (msg.equals("!slaves")) {
					updateSlaves(observer, msgc);
				} else if (msg.startsWith("!speed ")) {
					try {
						updateSpeed(observer, msgc);
					} catch (FormatterException e) {
						say("[speed] FormatterException: " + e.getMessage());
					}
				} else if (msg.equals("!df")) {
					try {
						updateDF(observer, msgc);
					} catch (FormatterException e) {
						say("[df] FormatterException: " + e.getMessage());
					}
				} else if (
					msg.equals("!who")
						|| msg.equals("!leechers")
						|| msg.equals("!uploaders")) {
					//					try {
					//						updateWho(observer, msgc);
					//					} catch (FormatterException e) {
					//						say("[who] FormatterException: " + e.getMessage());
					//					}
					//				} else if (msg.startsWith("replic")) {
					//					String args[] =
					//						msg.substring("replic ".length()).split(" ");
					//					// replic <user> <pass> <to-slave> <path>
					//					User user;
					//					try {
					//						user = _cm.getUserManager().getUserByName(args[0]);
					//					} catch (NoSuchUserException e) {
					//						_conn.sendCommand(
					//							new MessageCommand(
					//								msgc.getSource(),
					//								"replic: no such user"));
					//						logger.log(Level.INFO, "No such user", e);
					//						return;
					//					} catch (IOException e) {
					//						_conn.sendCommand(
					//							new MessageCommand(
					//								msgc.getSource(),
					//								"replic: userfile error"));
					//						logger.log(Level.WARN, "", e);
					//						return;
					//					}
					//					if (user.checkPassword(args[1])) {
					//						RemoteSlave rslave;
					//						try {
					//							rslave = _cm.getSlaveManager().getSlave(args[2]);
					//						} catch (ObjectNotFoundException e) {
					//							_conn.sendCommand(
					//								new MessageCommand(
					//									msgc.getSource(),
					//									"replic: No such slave: "
					//										+ e.getMessage()));
					//							return;
					//						}
					//						LinkedRemoteFile path;
					//						try {
					//							path =
					//								_cm.getSlaveManager().getRoot().lookupFile(
					//									args[3]);
					//						} catch (FileNotFoundException e) {
					//							logger.info("", e);
					//							_conn.sendCommand(
					//								new MessageCommand(
					//									msgc.getSource(),
					//									"replic: File not found: "
					//										+ e.getMessage()));
					//							return;
					//						}
					//						try {
					//							path.replicate(rslave);
					//						} catch (NoAvailableSlaveException e) {
					//							_conn.sendCommand(
					//								new MessageCommand(
					//									msgc.getSource(),
					//									"replic: No source slave for "
					//										+ path.getPath()
					//										+ ": "
					//										+ e.getMessage()));
					//						} catch (IOException e) {
					//							_conn.sendCommand(
					//								new MessageCommand(
					//									msgc.getSource(),
					//									"IO Error: " + e.getMessage()));
					//							logger.warn("", e);
					//						}
					//					}
				}
			}
			// Don't bother martyr with our exceptions.
			// It wouldn't know what to do with them anyway.
		} catch (RuntimeException t) {
			logger.log(Level.WARN, "Exception in IRC message handler", t);
		}
	}

	private void updateBw(Observable observer, MessageCommand command)
		throws FormatterException {
		assert _cm != null;
		assert _cm.getSlaveManager() != null;
		SlaveStatus status = _cm.getSlaveManager().getAllStatus();

		ReplacerEnvironment env =
			new ReplacerEnvironment(IRCListener.GLOBAL_ENV);

		fillEnvSpace(env, status);

		say(ReplacerUtils.jprintf("bw", env, IRCListener.class));
	}

	private void updateDF(Observable observer, MessageCommand msgc)
		throws FormatterException {
		SlaveStatus status = getSlaveManager().getAllStatus();
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);

		fillEnvSpace(env, status);

		say(ReplacerUtils.jprintf("diskfree", env, IRCListener.class));
	}

	private void updateSlaves(Observable observer, MessageCommand updated) {
	}

	private void updateSpeed(Observable observer, MessageCommand msgc)
		throws FormatterException {
		String username;
		try {
			username = msgc.getMessage().substring("!speed ".length());
		} catch (ArrayIndexOutOfBoundsException e) {
			logger.warn("", e);
			return;
		} catch (StringIndexOutOfBoundsException e) {
			logger.warn("", e);
			return;
		}
		ReplacerEnvironment env = new ReplacerEnvironment(GLOBAL_ENV);
		env.add("user", username);
		String status =
			ReplacerUtils.jprintf("speed.pre", env, IRCListener.class);

		String separator =
			ReplacerUtils.jprintf("speed.separator", env, IRCListener.class);

		boolean first = true;

		Collection conns = getConnectionManager().getConnections();
		synchronized (conns) {
			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				try {
					User connUser = conn.getUser();
					if (!first) {
						status = status + separator;
					}
					if (connUser.getUsername().equals(username)) {

						env.add(
							"idle",
							(System.currentTimeMillis() - conn.getLastActive())
								/ 1000
								+ "s");

						if (_cm
							.getConfig()
							.checkHideInWho(
								connUser,
								conn.getCurrentDirectory()))
							continue;
						first = false;
						if (!conn.isExecuting()) {
							status
								+= ReplacerUtils.jprintf(
									"speed.idle",
									env,
									IRCListener.class);

						} else if (
							conn.getDataConnectionHandler().isTransfering()) {
							try {
								env.add(
									"speed",
									Bytes.formatBytes(
										conn
											.getDataConnectionHandler()
											.getTransfer()
											.getXferSpeed())
										+ "/s");
							} catch (RemoteException e2) {
								logger.warn("", e2);
							}
							env.add(
								"file",
								conn
									.getDataConnectionHandler()
									.getTransferFile()
									.getName());
							env.add(
								"slave",
								conn
									.getDataConnectionHandler()
									.getTranferSlave()
									.getName());

							if (conn.getTransferDirection()
								== Transfer.TRANSFER_RECEIVING_UPLOAD) {
								status
									+= ReplacerUtils.jprintf(
										"speed.up",
										env,
										IRCListener.class);

							} else if (
								conn.getTransferDirection()
									== Transfer.TRANSFER_SENDING_DOWNLOAD) {
								status
									+= ReplacerUtils.jprintf(
										"speed.down",
										env,
										IRCListener.class);
							}
						}
					}
				} catch (NoSuchUserException e) {
					//just continue.. we aren't interested in connections without logged-in users
				}
			} // for
		}
		status += ReplacerUtils.jprintf("speed.post", env, IRCListener.class);
		if (first) {
			status =
				ReplacerUtils.jprintf("speed.error", env, IRCListener.class);
		}
		say(status);
	}

	public void init(ConnectionManager mgr) {
		_cm = mgr;
		try {
			reload();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public String getChannelName() {
		return _channelName;
	}

	public void unload() {

	}

	public void reconnect() {
		_conn.disconnect();
	}

}
