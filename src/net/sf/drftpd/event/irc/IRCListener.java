/*
* Created on 2003-aug-03
*
* To change the template for this generated file go to
* Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
*/
package net.sf.drftpd.event.irc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.Nukee;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.event.InviteEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.slave.Transfer;

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

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class IRCListener implements FtpListener, Observer {

	public class Ret {
		public String format;
		public LinkedRemoteFile section;
		public Ret(String format, LinkedRemoteFile dir) {
			this.format = format;
			this.section = dir;
		}
	}
	private static Logger logger = Logger.getLogger(IRCListener.class);

	/**
	 * @deprecated use libreplace
	 * @param user
	 * @return
	 */
	public static String formatUser(User user) {
		return user.getUsername() + "/" + user.getGroupName();
	}

	/**
	 * Used from FtpConnection
	 * @param files Collection of LinkedRemoteFile objects
	 * @return
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

	private AutoReconnect _autoReconnect;
	private String _channelName;

	private ClientState _clientState;
	private ConnectionManager _cm;

	private IRCConnection _conn;
	private Properties _ircCfg;
	private String _key;
	private int _port;

	private String _server;
	private static final ReplacerEnvironment globalEnv = new ReplacerEnvironment();
	static {
		globalEnv.add("bold", "\u0002");
		globalEnv.add("coloroff", "\u000f");
		globalEnv.add("color", "\u0003");
	}

	/**
	 * 
	 */
	public IRCListener()
		throws UnknownHostException, IOException {

		//_cm = cm;
		//Debug.setDebugLevel(Debug.FAULT);
		Debug.setOutputStream(
			new PrintStream(new FileOutputStream("ftp-data/logs/sitebot.log")));

		reload();

		_clientState = new ClientState();
		_conn = new IRCConnection(_clientState);

		_autoReconnect = new AutoReconnect(_conn);
		new AutoRegister(
			_conn,
			_ircCfg.getProperty("irc.nick"),
			_ircCfg.getProperty("irc.user"),
			_ircCfg.getProperty("irc.name"));
		new AutoJoin(_conn, _channelName, _key);
		new AutoResponder(_conn);
		_conn.addCommandObserver(this);
		logger.info(
			"IRCListener: connecting to " + _server + ":" + _port);
		_conn.connect(_server, _port);
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
				ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
				env.add("message", mevent.getMessage());
				say(SimplePrintf.jprintf(_ircCfg.getProperty("shutdown"), env));
			} else {
				logger.debug("Unhandled event: " + event);
			}
		} catch (FormatterException ex) {
			say(event.getCommand() + " FormatterException: " + ex.getMessage());
			logger.warn("", ex);
		}
	}

	private void actionPerformedDirectory(DirectoryFtpEvent direvent)
		throws FormatterException {

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
			String format = obj.format;
			LinkedRemoteFile dir = obj.section;

			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			fillEnvSection(env, direvent, dir);

			say(SimplePrintf.jprintf(format, env));

		} else if (direvent.getCommand().equals("STOR")) {
			actionPerformedDirectorySTOR(direvent);
		} else {
			logger.debug("Unhandled DirectoryEvent: " + direvent);
		}
	}

	/**
	 * @param direvent
	 */
	private void actionPerformedDirectorySTOR(DirectoryFtpEvent direvent)
		throws FormatterException {
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
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
					+ ", can't publish race info",
				ex);
			return;
		} catch (NoAvailableSlaveException e) {
			logger.info("No available slave with .sfv", e);
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
		env.add(
			"secondstocomplete",
			Long.toString((starttime - direvent.getTime()) / 1000));

		if (!sfvfile.hasFile(direvent.getDirectory().getName()))
			return;

		int halfway = (int) Math.ceil((double) sfvfile.size() / 2);
		///// start ///// start ////

		//check if new racer
		String username = direvent.getUser().getUsername();
		if (sfvfile.finishedFiles() != 1) {
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
					String format = obj.format;
					LinkedRemoteFile section = obj.section;

					//					ReplacerEnvironment env =
					//						new ReplacerEnvironment(globalEnv);
					fillEnvSection(
						env,
						direvent,
						section,
						direvent.getDirectory());
					env.add(
						"filesleft",
						Integer.toString(
							sfvfile.size() - sfvfile.finishedFiles()));

					say(SimplePrintf.jprintf(format, env));
				}
			}
		}

		//COMPLETE
		if (sfvfile.finishedFiles() == sfvfile.size()) {
			Collection racers = topFileUploaders(sfvfile.getFiles());
			Ret ret = getPropertyFileSuffix("store.complete", dir);
			String format = ret.format;
			LinkedRemoteFile section = ret.section;

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
			env.add("files", Integer.toString(sfvfile.size()));
			env.add("size", Bytes.formatBytes(sfvfile.getTotalBytes()));
			env.add("speed", Bytes.formatBytes(sfvfile.getXferspeed()) + "/s");
			say(SimplePrintf.jprintf(format, env));

			Ret ret2 = getPropertyFileSuffix("store.complete.racer", dir);
			ReplacerFormat raceformat;
			// already have section from ret.section
			raceformat = ReplacerFormat.createFormat(ret2.format);

			int position = 1;
			for (Iterator iter = racers.iterator(); iter.hasNext();) {
				UploaderPosition stat = (UploaderPosition) iter.next();

				User raceuser;
				try {
					raceuser =
						_cm.getUserManager().getUserByName(stat.getUsername());
				} catch (NoSuchUserException e2) {
					continue;
				} catch (IOException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(globalEnv);

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
			//HALFWAY
		} else if (sfvfile.size() >= 4 && sfvfile.finishedFiles() == halfway) {
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
			env.add("filesleft", Integer.toString(sfvfile.filesLeft()));
			User leaduser;
			try {
				leaduser =
					_cm.getUserManager().getUserByName(stat.getUsername());
				env.add("leaduser", leaduser.getUsername());
				env.add("leadgroup", leaduser.getGroupName());
			} catch (NoSuchUserException e3) {
				logger.log(Level.WARN, "", e3);
			} catch (IOException e3) {
				logger.log(Level.WARN, "", e3);
			}

			Ret obj = getPropertyFileSuffix("store.halfway", dir);
			String format = (String) obj.format;
			LinkedRemoteFile section = obj.section;

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
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
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
			say(SimplePrintf.jprintf(_ircCfg.getProperty("nuke"), env));

			ReplacerFormat raceformat =
				ReplacerFormat.createFormat(_ircCfg.getProperty("nuke.nukees"));

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
				} catch (IOException e2) {
					logger.log(Level.FATAL, "Error reading userfile", e2);
					continue;
				}
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(globalEnv);

				raceenv.add("user", raceuser.getUsername());
				raceenv.add("group", raceuser.getGroupName());

				raceenv.add("position", "" + position++);
				raceenv.add("size", Bytes.formatBytes(stat.getAmount()));

				say(SimplePrintf.jprintf(raceformat, raceenv));

			}
			if (nobodyAmount != 0) {
				ReplacerEnvironment raceenv =
					new ReplacerEnvironment(globalEnv);

				raceenv.add("user", "nobody");
				raceenv.add("group", "nogroup");

				raceenv.add("position", "?");
				raceenv.add("size", Bytes.formatBytes(nobodyAmount));

				say(SimplePrintf.jprintf(raceformat, raceenv));

			}
		} else if (cmd.equals("UNNUKE")) {
			say(SimplePrintf.jprintf(_ircCfg.getProperty("unnuke"), env));
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
	/**
	 * @param event
	 */
	private void actionPerformedSlave(SlaveEvent event)
		throws FormatterException {
		SlaveEvent sevent = (SlaveEvent) event;
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
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

			say(SimplePrintf.jprintf(_ircCfg.getProperty("addslave"), env));
		} else if (event.getCommand().equals("DELSLAVE")) {
			say(SimplePrintf.jprintf(_ircCfg.getProperty("delslave"), env));
		}
	}

	private void actionPerformedInvite(InviteEvent event) {
		String user = event.getCommand();
		logger.info(
			"Invited "
			+ user + " through SITE INVITE");
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

		LinkedRemoteFile dir;
		if (file.isFile()) {
			try {
				dir = file.getParentFile();
			} catch (FileNotFoundException e) {
				throw new FatalException(e);
			}
		} else {
			dir = file;
		}

		env.add(
			"path",
			strippath(dir.getPath().substring(section.getPath().length())));
		env.add("file", file.getName());
	}

	/**
	 * @param env
	 * @param status
	 */
	private void fillEnvSpace(ReplacerEnvironment env, SlaveStatus status) {
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

	/**
	 * 
	 */
	private FtpConfig getConfig() {
		return _cm.getConfig();
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}
	public IRCConnection getIRCConnection() {
		return _conn;
	}
	public Ret getPropertyFileSuffix(String prefix, LinkedRemoteFile dir) {
		String format = null;
		LinkedRemoteFile tmp = dir;
		try {
			while (true) {
				tmp = tmp.getParentFile();
				format = _ircCfg.getProperty(prefix + "." + dir.getPath());
				if (format != null) {
					return new Ret(format, dir);
				}
			}
		} catch (FileNotFoundException e) {
		}

		LinkedRemoteFile tmp2 = dir, tmp3 = dir;
		tmp = dir;
		try {
			while (true) {
				tmp = tmp.getParentFile();
				tmp3 = tmp2;
				tmp2 = tmp;
			}
		} catch (FileNotFoundException e1) {
			return new Ret(_ircCfg.getProperty(prefix), tmp3);
		}
	}
	public SlaveManagerImpl getSlaveManager() {
		return getConnectionManager().getSlaveManager();
	}

	/**
	 * 
	 */
	private void reload() throws FileNotFoundException, IOException {
		_ircCfg = new Properties();
		_ircCfg.load(new FileInputStream("irc.conf"));
		_server = _ircCfg.getProperty("irc.server");
		_port = Integer.parseInt(_ircCfg.getProperty("irc.port"));
		_channelName = _ircCfg.getProperty("irc.channel");
		_key = _ircCfg.getProperty("irc.key");
		if (_key.equals(""))
			_key = null;
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
			if(line.startsWith("#")) {
				int pos = line.indexOf(':');
				chan = line.substring(0, pos);
				line = line.substring(pos+1);
			} else {
				chan = _channelName;
			}
			_conn.sendCommand(new MessageCommand(chan, line));
		}

	}

	private void sayDirectorySection(DirectoryFtpEvent direvent, String string)
		throws FormatterException {

		Ret obj = getPropertyFileSuffix(string, direvent.getDirectory());
		String format = obj.format;
		LinkedRemoteFile section = obj.section;

		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
		fillEnvSection(env, direvent, section);

		say(SimplePrintf.jprintf(format, env));
	}

	public String strippath(String path) {
		if (!path.startsWith("/")) {
			logger.debug(
				"Path didn't start with /, unneeded call to strippath()?",
				new Throwable());
			return path;
		}
		return path.substring(1);
	}

	public void update(Observable observer, Object updated) {
		try {
			if (updated instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) updated;
				//only accept messages from _channelName
				if(!msgc.getDest().equalsIgnoreCase(_channelName)) return;
				String message = msgc.getMessage();

				if (message.equals("!help")) {
					say("Available commands: !bw !slaves !speed !who");
				} else if (message.equals("!bw")) {
					try {
						updateBw(observer, msgc);
					} catch (FormatterException e) {
						say("[bw] FormatterException: " + e.getMessage());
					}
				} else if (message.equals("!slaves")) {
					updateSlaves(observer, msgc);
				} else if (message.startsWith("!speed")) {
					try {
						updateSpeed(observer, msgc);
					} catch (FormatterException e) {
						say("[speed] FormatterException: " + e.getMessage());
					}
				} else if (message.equals("!df")) {
					try {
						updateDF(observer, msgc);
					} catch (FormatterException e) {
						say("[df] FormatterException: " + e.getMessage());
					}
				} else if (
					message.equals("!who")
						|| message.equals("!leechers")
						|| message.equals("!uploaders")) {
					try {
						updateWho(observer, msgc);
					} catch (FormatterException e) {
						say("[who] FormatterException: " + e.getMessage());
					}
				} else if (
					message.startsWith("!invite ")
						&& msgc.isPrivateToUs(_clientState)) {
					String args[] = message.split(" ");
					User user;
					try {
						user = _cm.getUserManager().getUserByName(args[1]);
					} catch (NoSuchUserException e) {
						logger.log(
							Level.WARN,
							args[1] + " " + e.getMessage(),
							e);
						return;
					} catch (IOException e) {
						logger.log(Level.WARN, "", e);
						return;
					}
					if (user.checkPassword(args[2])) {
						logger.info(
							"Invited "
								+ msgc.getSourceString()
								+ " as user "
								+ user.getUsername());
						_conn.sendCommand(
							new InviteCommand(msgc.getSource(), _channelName));
					} else {
						logger.log(
							Level.WARN,
							msgc.getSourceString()
								+ " attempted invite with bad password: "
								+ msgc);
					}
				} else if (message.startsWith("replic")) {
					String args[] =
						message.substring("replic ".length()).split(" ");
					// replic <user> <pass> <to-slave> <path>
					User user;
					try {
						user = _cm.getUserManager().getUserByName(args[0]);
					} catch (NoSuchUserException e) {
						_conn.sendCommand(
							new MessageCommand(
								msgc.getSource(),
								"replic: no such user"));
						logger.log(Level.INFO, "No such user", e);
						return;
					} catch (IOException e) {
						_conn.sendCommand(
							new MessageCommand(
								msgc.getSource(),
								"replic: userfile error"));
						logger.log(Level.WARN, "", e);
						return;
					}
					if (user.checkPassword(args[1])) {
						RemoteSlave rslave;
						try {
							rslave = _cm.getSlaveManager().getSlave(args[2]);
						} catch (ObjectNotFoundException e) {
							_conn.sendCommand(
								new MessageCommand(
									msgc.getSource(),
									"replic: No such slave: "
										+ e.getMessage()));
							return;
						}
						LinkedRemoteFile path;
						try {
							path =
								_cm.getSlaveManager().getRoot().lookupFile(
									args[3]);
						} catch (FileNotFoundException e) {
							logger.info("", e);
							_conn.sendCommand(
								new MessageCommand(
									msgc.getSource(),
									"replic: File not found: "
										+ e.getMessage()));
							return;
						}
						try {
							path.replicate(rslave);
						} catch (NoAvailableSlaveException e) {
							_conn.sendCommand(
								new MessageCommand(
									msgc.getSource(),
									"replic: No source slave for "
										+ path.getPath()
										+ ": "
										+ e.getMessage()));
						} catch (IOException e) {
							_conn.sendCommand(
								new MessageCommand(
									msgc.getSource(),
									"IO Error: " + e.getMessage()));
							logger.warn("", e);
						}
					}
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
		SlaveStatus status = _cm.getSlaveManager().getAllStatus();

		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);

		fillEnvSpace(env, status);

		say(SimplePrintf.jprintf(_ircCfg.getProperty("bw"), env));
	}

	/**
	 * @param observer
	 * @param msgc
	 */
	private void updateDF(Observable observer, MessageCommand msgc)
		throws FormatterException {
		SlaveStatus status = getSlaveManager().getAllStatus();
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);

		fillEnvSpace(env, status);

		say(SimplePrintf.jprintf(_ircCfg.getProperty("diskfree"), env));
	}

	private void updateSlaves(Observable observer, MessageCommand updated) {
		for (Iterator iter = _cm.getSlaveManager().getSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			String statusString;

			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			env.add("slave", rslave.getName());

			try {
				SlaveStatus status;
				try {
					status = rslave.getSlave().getSlaveStatus();
				} catch (NoAvailableSlaveException e1) {
					say(
						SimplePrintf.jprintf(
							_ircCfg.getProperty("slaves.offline"),
							env));
					continue;
				}

				env.add("xfers", Integer.toString(status.getTransfers()));
				env.add(
					"throughput",
					Bytes.formatBytes(status.getThroughput()) + "/s");

				env.add(
					"xfersup",
					Integer.toString(status.getTransfersReceiving()));
				env.add(
					"throughputup",
					Bytes.formatBytes(status.getThroughputReceiving()) + "/s");

				env.add(
					"xfersdown",
					Integer.toString(status.getTransfersSending()));
				env.add(
					"throughputdown",
					Bytes.formatBytes(status.getThroughputSending()));

				fillEnvSpace(env, status);

				statusString =
					SimplePrintf.jprintf(_ircCfg.getProperty("slaves"), env);
			} catch (ConnectException e) {
				rslave.handleRemoteException(e);
				statusString = "offline";
			} catch (FormatterException e) {
				say("[slaves] formatterexception: " + e.getMessage());
				return;
			} catch (RuntimeException t) {
				logger.log(
					Level.WARN,
					"Caught RuntimeException in !slaves loop",
					t);
				statusString = "RuntimeException";
			} catch (RemoteException e) {
				rslave.handleRemoteException(e);
				statusString = "offline";
			}
			say(statusString);
		}
	}

	private void updateSpeed(Observable observer, MessageCommand msgc)
		throws FormatterException {
		String username;
		try {
			username = msgc.getMessage().substring("!speed ".length());
		} catch (ArrayIndexOutOfBoundsException e) {
			logger.warn("", e);
			return;
		}
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
		env.add("user", username);

		StringBuffer status =
			new StringBuffer(
				SimplePrintf.jprintf(
					_ircCfg.getProperty("speed.pre", ""),
					env));

		ReplacerFormat formatup;
		try {
			formatup =
				ReplacerFormat.createFormat(_ircCfg.getProperty("speed.up"));
		} catch (Throwable e) {
			logger.debug("", e);
			return;
		}
		ReplacerFormat formatdown =
			ReplacerFormat.createFormat(_ircCfg.getProperty("speed.down"));

		ReplacerFormat formatidle =
			ReplacerFormat.createFormat(_ircCfg.getProperty("speed.idle"));
		String separator =
			SimplePrintf.jprintf(
				_ircCfg.getProperty("speed.separator", ""),
				env);
		boolean first = true;

		Collection conns = getConnectionManager().getConnections();
		synchronized (conns) {
			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				try {
					User connUser = conn.getUser();
					if (!first) {
						status.append(separator);
					}
					if (conn.isAuthenticated()
						&& conn.getUser().getUsername().equals(username)) {

						env.add(
							"idle",
							(System.currentTimeMillis() - conn.getLastActive())
								/ 1000
								+ "s");

						if (!conn.isExecuting()) {
							if (getConfig()
								.checkHideInWho(
									connUser,
									conn.getTransferFile()))
								continue;
							first = false;
							status.append(
								SimplePrintf.jprintf(formatidle, env));

						} else if (conn.isTransfering()) {
							if (_cm
								.getConfig()
								.checkHideInWho(
									connUser,
									conn.getCurrentDirectory()))
								continue;
							first = false;
							if (conn.isTransfering()) {
								try {
									env.add(
										"speed",
										Bytes.formatBytes(
											conn.getTransfer().getXferSpeed())
											+ "/s");
								} catch (RemoteException e2) {
									logger.warn("", e2);
								}
								env.add(
									"file",
									conn.getTransferFile().getName());
								env.add(
									"slave",
									conn.getTranferSlave().getName());
							}

							if (conn.getTransferDirection()
								== Transfer.TRANSFER_RECEIVING_UPLOAD) {
								status.append(
									SimplePrintf.jprintf(formatup, env));

							} else if (
								conn.getTransferDirection()
									== Transfer.TRANSFER_SENDING_DOWNLOAD) {
								status.append(
									SimplePrintf.jprintf(formatdown, env));
							}
						}
					}
				} catch (FormatterException e) {
					say("speed: formatterexception: " + e.getMessage());
				} catch (NoSuchUserException e) {
					//just continue.. we aren't interested in connections without logged-in users
				}
			} // for
		}

		status.append(
			SimplePrintf.jprintf(_ircCfg.getProperty("speed.post", ""), env));
		say(status.toString());
	}

	/**
	 * @param observer
	 * @param msgc
	 */
	private void updateWho(Observable observer, MessageCommand msgc)
		throws FormatterException {
		ReplacerFormat formatup =
			ReplacerFormat.createFormat(_ircCfg.getProperty("who.up"));
		ReplacerFormat formatdown =
			ReplacerFormat.createFormat(_ircCfg.getProperty("who.down"));
		ReplacerFormat formatidle =
			ReplacerFormat.createFormat(_ircCfg.getProperty("who.idle"));

		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
		String command = msgc.getMessage();
		boolean up, dn, idle;
		if (command.equals("!who")) {
			up = dn = idle = true;
		} else {
			dn = command.equals("!leechers");
			up = command.equals("!uploaders");
			idle = false;
		}
		Collection conns = getConnectionManager().getConnections();
		synchronized (conns) {
			for (Iterator iter = conns.iterator(); iter.hasNext();) {
				BaseFtpConnection conn = (BaseFtpConnection) iter.next();
				if (conn.isAuthenticated()) {
					User user;
					try {
						user = conn.getUser();
					} catch (NoSuchUserException e) {
						continue;
					}
					if (getConfig()
						.checkHideInWho(user, conn.getCurrentDirectory()))
						continue;
					StringBuffer status = new StringBuffer();
					env.add(
						"idle",
						(System.currentTimeMillis() - conn.getLastActive())
							/ 1000
							+ "s");
					env.add("user", user.getUsername());

					if (!conn.isExecuting()) {
						if (idle)
							status.append(
								SimplePrintf.jprintf(formatidle, env));

					} else if (conn.isTransfering()) {
						try {
							env.add(
								"speed",
								Bytes.formatBytes(
									conn.getTransfer().getXferSpeed())
									+ "/s");
						} catch (RemoteException e2) {
							logger.warn("", e2);
						}
						env.add("file", conn.getTransferFile().getName());
						env.add("slave", conn.getTranferSlave().getName());

						if (conn.getTransferDirection()
							== Transfer.TRANSFER_RECEIVING_UPLOAD) {
							if (up)
								status.append(
									SimplePrintf.jprintf(formatup, env));

						} else if (
							conn.getTransferDirection()
								== Transfer.TRANSFER_SENDING_DOWNLOAD) {
							if (dn)
								status.append(
									SimplePrintf.jprintf(formatdown, env));
						}
					}
					say(status.toString());
				}
			}
		}
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#init(net.sf.drftpd.master.command.CommandManager)
	 */
	public void init(ConnectionManager mgr) {
		_cm = mgr;
	}

	public String getChannelName() {
		return _channelName;
	}
}
