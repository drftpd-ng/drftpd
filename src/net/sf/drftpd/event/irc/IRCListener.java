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
import java.util.logging.Level;
import java.util.logging.Logger;

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
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.clientstate.ClientState;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.commands.RawCommand;

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
		logger.setLevel(Level.FINEST);
	}

	private IRCConnection _ircConnection;

	private String _server;
	private int _port;
	private String _channelName;
	Channel _mainChannel;
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
		_ircConnection = new IRCConnection(_clientState);

		_autoReconnect = new AutoReconnect(_ircConnection);
		new AutoRegister(
			_ircConnection,
			_ircCfg.getProperty("irc.nick"),
			_ircCfg.getProperty("irc.user"),
			_ircCfg.getProperty("irc.name"));
		new AutoJoin(_ircConnection, _channelName, _key);
		new AutoResponder(_ircConnection);
		_ircConnection.addCommandObserver(this);
		System.out.println(
			"IRCListener: connecting to " + _server + ":" + _port);
		_ircConnection.connect(_server, _port);

		globalEnv = new ReplacerEnvironment();
		globalEnv.add("bold", "\u0002");
		globalEnv.add("coloroff", "\u000f");
		globalEnv.add("color", "\u0003");

	}

	public void update(Observable observer, Object updated) {
		try {
			if (updated instanceof MessageCommand) {
				MessageCommand msgc = (MessageCommand) updated;
				String message = msgc.getMessage();
				//FullNick source = msgc.getSource();

				if (message.equals("!help")) {
					_ircConnection.sendCommand(
						new MessageCommand(
							_channelName,
							"Available commands: !bw !slaves"));
				} else if (message.equals("!bw")) {
					SlaveStatus status = _cm.getSlavemanager().getAllStatus();

					int idlers = 0;
					int total = 0;
					for (Iterator iter = _cm.getConnections().iterator();
						iter.hasNext();
						) {
						BaseFtpConnection conn =
							(BaseFtpConnection) iter.next();
						total++;
						if (!conn.isExecuting())
							idlers++;
					}
					//[ total: 1 of 32 / 297kb/sec ] - [ up: 1 / 297kb/sec | dn: 0 / 0kb/sec | idle: 0 ]
					ReplacerEnvironment env =
						new ReplacerEnvironment(globalEnv);

					env.add("xfers", Integer.toString(status.getTransfers()));
					env.add(
						"throughput",
						Bytes.formatBytes(status.getThroughput()));

					env.add(
						"xfersup",
						Integer.toString(status.getTransfersReceiving()));
					env.add(
						"throughputup",
						Bytes.formatBytes(status.getThroughputReceiving()));

					env.add(
						"xfersdn",
						Integer.toString(status.getTransfersSending()));
					env.add(
						"throughputdn",
						Bytes.formatBytes(status.getThroughputSending()));

					env.add(
						"spacetotal",
						Long.toString(status.getDiskSpaceCapacity()));
					env.add(
						"spacefree",
						Long.toString(status.getDiskSpaceAvailable()));
					env.add(
						"spaceused",
						Long.toString(status.getDiskSpaceUsed()));

					try {
						say(
							SimplePrintf.jprintf(
								_ircCfg.getProperty("bw"),
								env));
					} catch (FormatterException e) {
						logger.log(Level.WARNING, "", e);
					}

					//					say(
					//						"[ total: "
					//							+ status.getTransfers()
					//							+ " / "
					//							+ Bytes.formatBytes(status.getThroughput())
					//							+ "/s ] [ up "
					//							+ status.getTransfersReceiving()
					//							+ " / "
					//							+ Bytes.formatBytes(status.getThroughputReceiving())
					//							+ "/s | dn "
					//							+ status.getTransfersSending()
					//							+ " / "
					//							+ Bytes.formatBytes(status.getThroughputSending())
					//							+ "/s idle: "
					//							+ idlers
					//							+ " ]  [ space "
					//							+ Bytes.formatBytes(status.getDiskSpaceAvailable())
					//							+ " / "
					//							+ Bytes.formatBytes(status.getDiskSpaceCapacity())
					//							+ " ]");
				} else if (message.equals("!slaves")) {
					for (Iterator iter =
						_cm.getSlavemanager().getSlaves().iterator();
						iter.hasNext();
						) {
						RemoteSlave rslave = (RemoteSlave) iter.next();
						String statusString;
						try {
							SlaveStatus status =
								rslave.getSlave().getSlaveStatus();
							ReplacerFormat format =
								ReplacerFormat.createFormat(
									"[ xfers total: ${totalxfers} ${totalthroughput}/s ] [ up ${upxfers} ${upthroughput} ] [ down ${downxfers} ${downthroughput} ]");
							ReplacerEnvironment env = new ReplacerEnvironment();

							env.add(
								"totalxfers",
								new Integer(status.getTransfers()));
							env.add(
								"totalthroughput",
								Bytes.formatBytes(status.getThroughput())
									+ "/s");

							env.add(
								"upxfers",
								new Integer(status.getTransfersReceiving()));
							env.add(
								"upthroughput",
								Bytes.formatBytes(
									status.getThroughputReceiving())
									+ "/s");

							env.add(
								"downxfers",
								new Integer(status.getTransfersSending()));
							env.add(
								"downthroughput",
								Bytes.formatBytes(
									status.getThroughputSending()));
							statusString = SimplePrintf.jprintf(format, env);
							//							statusString =
							//								"[xfers total: "
							//									+ status.getTransfers()
							//									+ " "
							//									+ Bytes.formatBytes(status.getThroughput())
							//									+ "/s] [xfers up "
							//									+ status.getTransfersReceiving()
							//									+ ", "
							//									+ Bytes.formatBytes(
							//										status.getThroughputReceiving())
							//									+ "/s] [xfers down "
							//									+ status.getTransfersSending()
							//									+ ", "
							//									+ Bytes.formatBytes(
							//										status.getThroughputSending())
							//									+ "/s] [space free: "
							//									+ Bytes.formatBytes(
							//										status.getDiskSpaceAvailable())
							//									+ "/"
							//									+ Bytes.formatBytes(
							//										status.getDiskSpaceCapacity())
							//									+ "]";
						} catch (ConnectException e) {
							rslave.handleRemoteException(e);
							statusString = "offline";
						} catch (NoAvailableSlaveException e) {
							statusString = "offline";
						} catch (Throwable t) {
							logger.log(
								Level.WARNING,
								"Caught throwable in !slaves loop",
								t);
							statusString = "offline";
						}
						say(
							"[slaves] "
								+ rslave.getName()
								+ " "
								+ statusString);
					}
				} else if (message.startsWith("!speed")) {
					String username;
					try {
						username = message.substring("!speed ".length());
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
						BaseFtpConnection conn =
							(BaseFtpConnection) iter.next();
						if (_cm
							.getConfig()
							.checkHideInWho(conn.getCurrentDirectory()))
							continue;
						if (conn.isAuthenticated()
							&& conn.getUser().getUsername().equals(username)) {
							ReplacerEnvironment env = new ReplacerEnvironment();
							env.add("username", conn.getUser().getUsername());
							env.add(
								"idle",
								(System.currentTimeMillis()
									- conn.getLastActive())
									/ 1000
									+ "s");

							if (!conn.isExecuting()) {
								status =
									status
										+ SimplePrintf.jprintf(formatidle, env);

							} else if (conn.isTransfering()) {
								if (conn.isTransfering()) {
									env.add(
										"speed",
										Bytes.formatBytes(
											conn
												.getTransfer()
												.getTransferSpeed())
											+ "/s");
									env.add(
										"file",
										conn.getTransferFile().getName());
									env.add(
										"slave",
										conn.getTranferSlave().getName());
								}

								if (conn.getTransferDirection()
									== Transfer.TRANSFER_RECEIVING_UPLOAD) {
									status =
										status
											+ SimplePrintf.jprintf(formatup, env);

								} else if (
									conn.getTransferDirection()
										== Transfer.TRANSFER_SENDING_DOWNLOAD) {
									status =
										status
											+ SimplePrintf.jprintf(
												formatdown,
												env);
								}
							}
						}
					}
					say(status);
				} else if (
					message.startsWith("!invite ")
						&& msgc.isPrivateToUs(_clientState)) {
					String args[] = message.split(" ");
					User user = _cm.getUsermanager().getUserByName(args[1]);
					if (user.checkPassword(args[2])) {
						_ircConnection.sendCommand(
							new RawCommand(
								"INVITE "
									+ msgc.getSource().getNick()
									+ " "
									+ _channelName));
					} else {
						logger.log(
							Level.WARNING,
							"!invite with wrong password: " + msgc);
					}

				}
			}
		} catch (Throwable t) {
			logger.log(Level.WARNING, "Exception in IRC message handler", t);
		}
	}

	private void say(String message) {
		String lines[] = message.split("\n");
		for (int i = 0; i < lines.length; i++) {
			_ircConnection.sendCommand(
				new MessageCommand(_channelName, lines[i]));
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
				logger.log(Level.WARNING, "", e);
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
		try {
			while (true) {
				dir = dir.getParentFile();
				format = _ircCfg.getProperty(prefix + "." + dir.getPath());
				if (format != null) {
					return new Object[] { format, dir };
				}
			}
		} catch (FileNotFoundException e) {
		}

		LinkedRemoteFile tmp = dir, tmp2 = dir;
		try {
			while (true) {
				tmp = tmp.getParentFile();
				tmp2 = tmp;
				//dir = /
				//tmp = /<dir>/
			}
		} catch (FileNotFoundException e1) {
			return new Object[] { _ircCfg.getProperty(prefix), tmp2 };
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
				logger.log(Level.WARNING, format, e1);
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
				logger.log(Level.SEVERE, "lookupSFVFile failed", e1);
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
					LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
					if (file == direvent.getDirectory())
						continue;
					if (file.getUsername().equals(username))
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
							logger.log(Level.WARNING, "", e2);
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
					logger.log(Level.SEVERE, "", e6);
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
					logger.log(Level.WARNING, "", e3);
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
					logger.log(Level.WARNING, "", e4);
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
						logger.log(Level.SEVERE, "Error reading userfile", e2);
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
						logger.log(Level.WARNING, "", e5);
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

				Object obj[] = getPropertyFileSuffix("store.halfway", dir);
				String format = (String) obj[0];
				LinkedRemoteFile section = (LinkedRemoteFile) obj[1];

				fillEnvSection(env, direvent, section);

				try {
					say(SimplePrintf.jprintf(format, env));
				} catch (FormatterException e2) {
					logger.log(Level.WARNING, "", e2);
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
				//								Level.SEVERE,
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
				logger.log(Level.WARNING, "", e);
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
		env.add("section", section.getPath());
		if (file.isFile() && file.getXfertime() != 0)
			env.add(
				"speed",
				Bytes.formatBytes(file.length() / file.getXfertime()) + "/s");
		env.add("path", file.getPath().substring(section.getPath().length()));
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
