/*
* Created on 2003-aug-03
*
* To change the template for this generated file go to
* Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
*/
package net.sf.drftpd.event.irc;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.UnknownHostException;
import java.rmi.ConnectException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Observable;
import java.util.Observer;
import java.util.Properties;

import net.sf.drftpd.Bytes;
import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.MessageEvent;
import net.sf.drftpd.event.NukeEvent;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
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
	private ReplacerEnvironment globalEnv;

	private static Logger logger =
		Logger.getLogger(IRCListener.class.getName());
	static {
		logger.setLevel(Level.ALL);
	}

	private IRCConnection _conn;

	private String _server;
	private int _port;
	private String _channelName;
	private String _key;

	private ClientState _clientState;

	private AutoReconnect _autoReconnect;
	private ConnectionManager _cm;
	private Properties _ircCfg;
	/**
	 * 
	 */
	public IRCListener(ConnectionManager cm, FtpConfig config, String args[])
		throws UnknownHostException, IOException {

		_cm = cm;
		Debug.setDebugLevel(Debug.FAULT);

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
		System.out.println(
			"IRCListener: connecting to " + _server + ":" + _port);
		_conn.connect(_server, _port);

		globalEnv = new ReplacerEnvironment();
		globalEnv.add("bold", "\u0002");
		globalEnv.add("coloroff", "\u000f");
		globalEnv.add("color", "\u0003");

	}

	public void updateBw(Observable observer, MessageCommand command) {
		SlaveStatus status = _cm.getSlavemanager().getAllStatus();

		int idlers = 0;
		int total = 0;
		for (Iterator iter = _cm.getConnections().iterator();
			iter.hasNext();
			) {
			BaseFtpConnection conn = (BaseFtpConnection) iter.next();
			total++;
			if (!conn.isExecuting())
				idlers++;
		}
		ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);

		env.add("xfers", Integer.toString(status.getTransfers()));
		env.add("throughput", Bytes.formatBytes(status.getThroughput()));

		env.add("xfersup", Integer.toString(status.getTransfersReceiving()));
		env.add(
			"throughputup",
			Bytes.formatBytes(status.getThroughputReceiving()));

		env.add("xfersdn", Integer.toString(status.getTransfersSending()));
		env.add(
			"througputdown",
			Bytes.formatBytes(status.getThroughputSending()));

		env.add("spacetotal", Long.toString(status.getDiskSpaceCapacity()));
		env.add("spacefree", Long.toString(status.getDiskSpaceAvailable()));
		env.add("spaceused", Long.toString(status.getDiskSpaceUsed()));

		try {
			say(SimplePrintf.jprintf(_ircCfg.getProperty("bw"), env));
		} catch (FormatterException e) {
			logger.log(Level.WARN, "", e);
		}
	}

	public void updateSlaves(Observable observer, MessageCommand updated) {
		for (Iterator iter = _cm.getSlavemanager().getSlaves().iterator();
			iter.hasNext();
			) {
			RemoteSlave rslave = (RemoteSlave) iter.next();
			String statusString;

			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			env.add("name", rslave.getName());

			try {
				SlaveStatus status;
				try {
					status = rslave.getSlave().getSlaveStatus();
				} catch (NoAvailableSlaveException e1) {
					say(SimplePrintf.jprintf(_ircCfg.getProperty("slaves.offline"), env));
					continue;
				}

				env.add("xfers", Integer.toString(status.getTransfers()));
				env.add(
					"throughput",
					Bytes.formatBytes(status.getThroughput()) + "/s");

				env.add("xfersup", Integer.toString(status.getTransfersReceiving()));
				env.add(
					"througputup",
					Bytes.formatBytes(status.getThroughputReceiving()) + "/s");

				env.add("xfersdown", Integer.toString(status.getTransfersSending()));
				env.add(
					"througputdown",
					Bytes.formatBytes(status.getThroughputSending()));

				env.add(
					"diskcapacity",
					Bytes.formatBytes(status.getDiskSpaceCapacity()));
				env.add(
					"diskavailable",
					Bytes.formatBytes(status.getDiskSpaceAvailable()));
				env.add(
					"diskused",
					Bytes.formatBytes(status.getDiskSpaceUsed()));

				statusString = SimplePrintf.jprintf(_ircCfg.getProperty("slaves"), env);
			} catch (ConnectException e) {
				rslave.handleRemoteException(e);
				statusString = "offline";
			} catch (FormatterException e) {
				say("[slaves] formatterexception: "+e.getMessage());
				return;
			} catch (RuntimeException t) {
				logger.log(Level.WARN, "Caught RuntimeException in !slaves loop", t);
				statusString = "RuntimeException";
			} catch (RemoteException e) {
				rslave.handleRemoteException(e);
				statusString ="offline";
			}
			say(statusString);
		}
	}

	public void updateSpeed(Observable observer, MessageCommand msgc)
		throws FormatterException {
		String username;
		try {
			username = msgc.getMessage().substring("!speed ".length());
		} catch (ArrayIndexOutOfBoundsException e) {
			return;
		}

		String status = "[who] " + username;
		ReplacerFormat formatup =
			ReplacerFormat.createFormat(
				" [ up : ${file} ${speed} to ${slave}]");
		ReplacerFormat formatdown =
			ReplacerFormat.createFormat(
				" [ dn : ${file} ${speed} from ${slave}]");
		ReplacerFormat formatidle =
			ReplacerFormat.createFormat(" [ idle : ${idle} ]");

		for (Iterator iter = _cm.getConnections().iterator();
			iter.hasNext();
			) {
			BaseFtpConnection conn = (BaseFtpConnection) iter.next();
			if (_cm.getConfig().checkHideInWho(conn.getCurrentDirectory()))
				continue;
			try {
				if (conn.isAuthenticated()
					&& conn.getUser().getUsername().equals(username)) {
					ReplacerEnvironment env = new ReplacerEnvironment();
					env.add("username", conn.getUser().getUsername());
					env.add(
						"idle",
						(System.currentTimeMillis() - conn.getLastActive())
							/ 1000
							+ "s");

					if (!conn.isExecuting()) {
						status = status + SimplePrintf.jprintf(formatidle, env);

					} else if (conn.isTransfering()) {
						if (conn.isTransfering()) {
							try {
								env.add(
									"speed",
									Bytes.formatBytes(
										conn.getTransfer().getTransferSpeed())
										+ "/s");
							} catch (RemoteException e2) {
								logger.warn("", e2);
							}
							env.add("file", conn.getTransferFile().getName());
							env.add("slave", conn.getTranferSlave().getName());
						}

						if (conn.getTransferDirection()
							== Transfer.TRANSFER_RECEIVING_UPLOAD) {
							status =
								status + SimplePrintf.jprintf(formatup, env);

						} else if (
							conn.getTransferDirection()
								== Transfer.TRANSFER_SENDING_DOWNLOAD) {
							status =
								status + SimplePrintf.jprintf(formatdown, env);
						}
					}
				}
			} catch (FormatterException e) {
				say("speed: formatterexception: "+e.getMessage());
			} catch (NoSuchUserException e) {
				//just continue.. we aren't interested in connections without logged-in users
			}
		}
		say(status);
	}
	public void update(Observable observer, Object updated) {
		try {
			if (updated instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) updated;
				String message = msgc.getMessage();

				if (message.equals("!help")) {
					say("Available commands: !bw !slaves");
				} else if (message.equals("!bw")) {
					updateBw(observer, msgc);
				} else if (message.equals("!slaves")) {
					updateSlaves(observer, msgc);
				} else if (message.startsWith("!speed")) {
					try {
						updateSpeed(observer, msgc);
					} catch (FormatterException e) {
						say("[speed] FormatterException: " + e.getMessage());
					}
				} else if (
					message.startsWith("!invite ")
						&& msgc.isPrivateToUs(_clientState)) {
					String args[] = message.split(" ");
					User user;
					try {
						user = _cm.getUsermanager().getUserByName(args[1]);
					} catch (NoSuchUserException e) {
						logger.log(Level.WARN, args[1] + " " + e.getMessage());
						return;
					} catch (IOException e) {
						logger.log(Level.WARN, "", e);
						return;
					}
					if (user.checkPassword(args[2])) {
						_conn.sendCommand(
							new InviteCommand(msgc.getSource(), _channelName));
					} else {
						logger.log(
							Level.WARN,
							"!invite with wrong password: " + msgc);
					}
				} else if (message.startsWith("replic")) {
					String args[] =
						message.substring("replic ".length()).split(" ");
					// replic <user> <pass> <to-slave> <path>
					User user;
					try {
						user = _cm.getUsermanager().getUserByName(args[0]);
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
							rslave = _cm.getSlavemanager().getSlave(args[2]);
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
								_cm.getSlavemanager().getRoot().lookupFile(
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
							_conn.sendCommand(new MessageCommand(msgc.getSource(), "IO Error: "+e.getMessage()));
							logger.warn("", e);
						}
					}
				}
			}
		} catch (RuntimeException t) {
			logger.log(Level.WARN, "Exception in IRC message handler", t);
		}
	}

	private void say(String message) {
		if (!_clientState.isOnChannel(_channelName))
			return;
		String lines[] = message.split("\n");
		for (int i = 0; i < lines.length; i++) {
			_conn.sendCommand(new MessageCommand(_channelName, lines[i]));
		}

	}

	/**
	 * @deprecated use libreplace
	 * @param user
	 * @return
	 */
	public static String formatUser(User user) {
		return user.getUsername() + "/" + user.getGroup();
	}

	public void actionPerformed(NukeEvent event) {
		String cmd = event.getCommand();
		if (cmd.equals("NUKE")) {

		} else if (cmd.equals("UNNUKE")) {

		}
	}
	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.FtpEvent)
	 */
	public void actionPerformed(Event event) {
		if (event instanceof DirectoryFtpEvent) {
			actionPerformed((DirectoryFtpEvent) event);
			return;
		} else if (event instanceof NukeEvent) {
			actionPerformed((NukeEvent) event);
		}

		if (event.getCommand().equals("RELOAD")) {

			try {
				reload();
			} catch (IOException e) {
				logger.log(Level.WARN, "", e);
			}

		} else if (event.getCommand().equals("ADDSLAVE")) {
			SlaveEvent sevent = (SlaveEvent) event;
			SlaveStatus status;
			try {
				status = sevent.getRSlave().getStatus();
			} catch (RemoteException e) {
				sevent.getRSlave().handleRemoteException(e);
				return;
			} catch (NoAvailableSlaveException e) {
				return;
			}
			say(
				"[addslave] "
					+ sevent.getRSlave().getName()
					+ " just came online with "
					+ Bytes.formatBytes(status.getDiskSpaceCapacity())
					+ " of disk space");
		} else if (event.getCommand().equals("DELSLAVE")) {
			SlaveEvent sevent = (SlaveEvent) event;
			say(
				"[slave] "
					+ sevent.getRSlave().getName()
					+ " just went offline");
		} else if (event.getCommand().equals("SHUTDOWN")) {
			MessageEvent mevent = (MessageEvent) event;
			say("[shutdown] " + mevent.getMessage());
		}
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

	public Object[] getPropertyFileSuffix(
		String prefix,
		LinkedRemoteFile dir) {
		String format = null;
		LinkedRemoteFile tmp = dir;
		try {
			while (true) {
				tmp = tmp.getParentFile();
				format = _ircCfg.getProperty(prefix + "." + dir.getPath());
				if (format != null) {
					return new Object[] { format, dir };
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
			return new Object[] { _ircCfg.getProperty(prefix), tmp3 };
		}
	}

	public void actionPerformed(DirectoryFtpEvent direvent) {
		if (_cm.getConfig().checkHideInWho(direvent.getDirectory()))
			return;

		if (direvent.getCommand().equals("MKD")) {

			Object obj[] =
				getPropertyFileSuffix("mkdir", direvent.getDirectory());
			String format = (String) obj[0];
			LinkedRemoteFile section = (LinkedRemoteFile) obj[1];

			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			//String section = section.getPath();
			fillEnvSection(env, direvent, section);
			try {
				say(SimplePrintf.jprintf(format, env));
			} catch (FormatterException e1) {
				logger.log(Level.WARN, format, e1);
			}

		} else if (direvent.getCommand().equals("STOR")) {
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
			} catch (ObjectNotFoundException ex) {
				logger.log(
					Level.INFO,
					"No sfv file in "
						+ direvent.getDirectory().getPath()
						+ ", can't publish race info");
				return;
			} catch (Exception e1) {
				logger.log(Level.FATAL, "lookupSFVFile failed", e1);
				return;
			}

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

						Object obj[] =
							getPropertyFileSuffix(
								"store.embraces",
								direvent.getDirectory());
						String format = (String) obj[0];
						LinkedRemoteFile section = (LinkedRemoteFile) obj[1];

						ReplacerEnvironment env =
							new ReplacerEnvironment(globalEnv);
						//						env.add("user", direvent.getUser().getUsername());
						//						env.add("group", direvent.getUser().getGroup());
						//						env.add("section", section.getPath());
						//						env.add(
						//							"path",
						//							dir.getPath().substring(
						//								section.getPath().length()));
						fillEnvSection(env, direvent, section);
						env.add(
							"filesleft",
							Integer.toString(
								sfvfile.size() - sfvfile.finishedFiles()));

						try {
							say(SimplePrintf.jprintf(format, env));
						} catch (FormatterException e2) {
							logger.log(Level.WARN, "", e2);
						}
					}
				}
			}

			if (sfvfile.finishedFiles() == sfvfile.size()) {
				Collection racers = topFileUploaders2(sfvfile.getFiles());
				Object obj[] = getPropertyFileSuffix("store.complete", dir);

				String format = (String) obj[0];
				LinkedRemoteFile section = (LinkedRemoteFile) obj[1];

				ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);

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
				env.add(
					"speed",
					Bytes.formatBytes(
						sfvfile.getTotalXfertime() / 1000 / sfvfile.size())
						+ "/s");
				try {
					say(SimplePrintf.jprintf(format, env));
				} catch (FormatterException e3) {
					logger.log(Level.WARN, "", e3);
				}
				//				say(
				//					"[complete] "
				//						+ dir.getPath()
				//						+ " was finished by "
				//						+ formatUser(direvent.getUser())
				//						+ " "
				//						+ racers.size()
				//						+ " users participated");
				Object obj2[] =
					getPropertyFileSuffix("store.complete.racer", dir);
				ReplacerFormat raceformat;
				try {
					raceformat = ReplacerFormat.createFormat((String) obj2[0]);
				} catch (FormatterException e4) {
					logger.log(Level.WARN, "", e4);
					return;
				}

				int position = 1;
				for (Iterator iter = racers.iterator(); iter.hasNext();) {
					UploaderPosition stat = (UploaderPosition) iter.next();

					User raceuser;
					try {
						raceuser =
							_cm.getUsermanager().getUserByName(
								stat.getUsername());
					} catch (NoSuchUserException e2) {
						continue;
					} catch (IOException e2) {
						logger.log(Level.FATAL, "Error reading userfile", e2);
						continue;
					}
					ReplacerEnvironment raceenv =
						new ReplacerEnvironment(globalEnv);
					raceenv.add("user", raceuser.getUsername());
					raceenv.add("group", raceuser.getGroup());

					raceenv.add("position", new Integer(position++));
					raceenv.add("size", Bytes.formatBytes(stat.getBytes()));
					raceenv.add("files", Integer.toString(stat.getFiles()));
					raceenv.add(
						"percent",
						Integer.toString(
							stat.getFiles() * 100 / sfvfile.size())
							+ "%");
					raceenv.add(
						"speed",
						Bytes.formatBytes(stat.getBytes() / stat.getFiles())
							+ "/s");

					try {
						say(SimplePrintf.jprintf(raceformat, raceenv));
					} catch (FormatterException e5) {
						logger.log(Level.WARN, "", e5);
					}

					//					say(
					//						str1
					//							+ " ["
					//							+ stat.getFiles()
					//							+ "f/"
					//							+ Bytes.formatBytes(stat.getBytes())
					//							+ "]");
				}
			} else if (
				sfvfile.size() >= 4 && sfvfile.finishedFiles() == halfway) {
				Collection uploaders = topFileUploaders2(sfvfile.getFiles());
				ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
				UploaderPosition stat =
					(UploaderPosition) uploaders.iterator().next();

				env.add(
					"leadspeed",
					Bytes.formatBytes(stat.getXfertime() / stat.getFiles())
						+ "/s");
				env.add("leadfiles", Integer.toString(stat.getFiles()));
				env.add("leadsize", Bytes.formatBytes(stat.getBytes()));
				env.add(
					"leadpercent",
					Integer.toString(stat.getFiles() * 100 / sfvfile.size())
						+ "%");
				env.add("filesleft", Integer.toString(sfvfile.filesLeft()));
				User leaduser;
				try {
					leaduser =
						_cm.getUsermanager().getUserByName(stat.getUsername());
					env.add("leaduser", leaduser.getUsername());
					env.add("leadgroup", leaduser.getGroup());
				} catch (NoSuchUserException e3) {
					logger.log(Level.WARN, "", e3);
				} catch (IOException e3) {
					logger.log(Level.WARN, "", e3);
				}

				Object obj[] = getPropertyFileSuffix("store.halfway", dir);
				String format = (String) obj[0];
				LinkedRemoteFile section = (LinkedRemoteFile) obj[1];

				fillEnvSection(env, direvent, section);

				try {
					say(SimplePrintf.jprintf(format, env));
				} catch (FormatterException e2) {
					logger.log(Level.WARN, "", e2);
					return;
				}

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

		} else if (direvent.getCommand().equals("RMD")) {
			Object obj[] =
				getPropertyFileSuffix("rmdir", direvent.getDirectory());
			String format = (String) obj[0];
			LinkedRemoteFile dir = (LinkedRemoteFile) obj[1];

			ReplacerEnvironment env = new ReplacerEnvironment(globalEnv);
			fillEnvSection(env, direvent, dir);

			try {
				say(SimplePrintf.jprintf(format, env));
			} catch (FormatterException e) {
				logger.log(Level.WARN, "", e);
			}

			//			say(
			//				"[deldir] "
			//					+ direvent.getDirectory().getPath()
			//					+ " was deleted by "
			//					+ formatUser(direvent.getUser()));
		} else if (direvent.getCommand().equals("WIPE")) {
			if (direvent.getDirectory().isDirectory()) {
				say(
					"[wipe] "
						+ direvent.getDirectory().getPath()
						+ " was wiped by "
						+ formatUser(direvent.getUser()));
			}
		}

	}

	public String strippath(String path) {
		if (path.startsWith("/"))
			path = path.substring(1);
		if (path.endsWith("/"))
			path = path.substring(0, path.length() - 1);
		return path;
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
		env.add("group", direvent.getUser().getGroup());
		env.add("section", strippath(section.getPath()));
		if (file.isFile() && file.getXfertime() != 0)
			env.add(
				"speed",
				Bytes.formatBytes(file.length() / file.getXfertime()) + "/s");
		if(file.isFile() && file.getXfertime() == 0)
		env.add("speed", "speed unknown");
		
		env.add(
			"path",
			strippath(file.getPath().substring(section.getPath().length())));
	}

	public static Collection topFileUploaders2(Collection files) {
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
}
