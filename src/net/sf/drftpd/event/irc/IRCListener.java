/*
* Created on 2003-aug-03
*
* To change the template for this generated file go to
* Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
*/
package net.sf.drftpd.event.irc;

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

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class IRCListener implements FtpListener, Observer {
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
	/**
	 * 
	 */
	public IRCListener(ConnectionManager cm, FtpConfig config, String args[])
		throws UnknownHostException, IOException {
		_cm = cm;
		Properties cfg = _cm.getPropertiesConfig();
		Debug.setDebugLevel(Debug.FAULT);

		_server = cfg.getProperty("irc.server");
		_port = Integer.parseInt(cfg.getProperty("irc.port"));
		_channelName = cfg.getProperty("irc.channel");
		_key = cfg.getProperty("irc.key");
		if (_key.equals(""))
			_key = null;

		_clientState = new ClientState();
		_ircConnection = new IRCConnection(_clientState);

		_autoReconnect = new AutoReconnect(_ircConnection);
		new AutoRegister(
			_ircConnection,
			cfg.getProperty("irc.nick"),
			cfg.getProperty("irc.user"),
			cfg.getProperty("irc.name"));
		new AutoJoin(_ircConnection, _channelName, _key);
		new AutoResponder(_ircConnection);
		_ircConnection.addCommandObserver(this);
		System.out.println(
			"IRCListener: connecting to " + _server + ":" + _port);
		_ircConnection.connect(_server, _port);
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
					say(
						"[ total: "
							+ status.getTransfers()
							+ " / "
							+ Bytes.formatBytes(status.getThroughput())
							+ "/s ] [ up "
							+ status.getTransfersReceiving()
							+ " / "
							+ Bytes.formatBytes(status.getThroughputReceiving())
							+ "/s | dn "
							+ status.getTransfersSending()
							+ " / "
							+ Bytes.formatBytes(status.getThroughputSending())
							+ "/s idle: "
							+ idlers
							+ " ]  [ space "
							+ Bytes.formatBytes(status.getDiskSpaceAvailable())
							+ " / "
							+ Bytes.formatBytes(status.getDiskSpaceCapacity())
							+ " ]");
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
									"[ xfers total: ${totalxfers,0} ${totalthroughput,0}/s ] [ up ${upxfers} ${upthroughput} ] [ down ${downxfers} ${downthroughput} ]");
							ReplacerEnvironment env = new ReplacerEnvironment();

							env.add(
								"totalxfers",
								new Integer(status.getTransfers()));
							env.add(
								"totalthroughput",
								Bytes.formatBytes(status.getThroughput())+"/s");

							env.add(
								"upxfers",
								new Integer(status.getTransfersReceiving()));
							env.add(
								"upthroughput",
								Bytes.formatBytes(
									status.getThroughputReceiving())+"/s");

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
					} catch (ArrayIndexOutOfBoundsException e) { return; }

					String status = "[who] "+username;
					ReplacerFormat formatup =
						ReplacerFormat.createFormat(" [ up : ${file,0} ${speed,0} ]");
					ReplacerFormat formatdown =
						ReplacerFormat.createFormat(" [ dn : ${file,0} ${speed,0} ]");
					ReplacerFormat formatidle =
						ReplacerFormat.createFormat(" [ idle : ${user,0} ]");
						
					for (Iterator iter = _cm.getConnections().iterator();
						iter.hasNext();
						) {
						BaseFtpConnection conn =
							(BaseFtpConnection) iter.next();
						if (conn.isAuthenticated() && conn.getUser().getUsername().equals(username)) {
							ReplacerEnvironment env = new ReplacerEnvironment();
							env.add("username", conn.getUser().getUsername());
							env.add(
								"speed",
								new Integer(
									Bytes.formatBytes(conn.getTransfer().getTransferSpeed())+"/s"));
							env.add("file", conn.getTransferFile());
							env.add("idle", System.currentTimeMillis()-conn.getLastActive()/1000+"s");

							if (!conn.isExecuting()) {
								status =
									status
										+ SimplePrintf.jprintf(formatidle, env);

							} else if (
								conn.getTransferDirection()
									== Transfer.TRANSFER_RECEIVING_UPLOAD) {
								status =
									status
										+ SimplePrintf.jprintf(formatup, env);

							} else if (
								conn.getTransferDirection()
									== Transfer.TRANSFER_SENDING_DOWNLOAD) {
								status =
									status
										+ SimplePrintf.jprintf(formatdown, env);
							}
						}
					}
					say(status);
				}
			}
		} catch (Throwable t) {
			logger.log(Level.WARNING, "Exception in IRC message handler", t);
		}
	}

	private void say(String message) {
		_ircConnection.sendCommand(new MessageCommand(_channelName, message));
	}

	public static String formatUser(User user) {
		return user.getUsername() + "/" + user.getGroup();
	}

	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.FtpEvent)
	 */
	public void actionPerformed(Event event) {
		System.out.println(
			"IRCListener.actionPerformed(): " + event.getCommand());
		if (event instanceof DirectoryFtpEvent) {
			actionPerformed((DirectoryFtpEvent) event);
			return;
		}
		if (event.getCommand().equals("ADDSLAVE")) {
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

	public void actionPerformed(DirectoryFtpEvent direvent) {
		if (direvent.getCommand().equals("MKD")) {
			say(
				"[newdir] "
					+ direvent.getDirectory().getPath()
					+ " was created by "
					+ formatUser(direvent.getUser()));

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
			int finishedFiles = sfvfile.finishedFiles();

			int halfway = (int) Math.ceil((double) sfvfile.size() / 2);
			///// start ///// start ////

			//check if new racer
			String username = direvent.getUser().getUsername();
			if (finishedFiles != 1) {
				for (Iterator iter = sfvfile.getFiles().iterator();
					iter.hasNext();
					) {
					LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
					if (file == direvent.getDirectory())
						continue;
					if (file.getUsername().equals(username))
						break;
					if (!iter.hasNext()) {
						say(
							dir.getPath()
								+ " embraces "
								+ formatUser(direvent.getUser()));
					}
				}
			}

			if (finishedFiles == sfvfile.size()) {
				Collection racers = topFileUploaders2(sfvfile.getFiles());
				say(
					"[complete] "
						+ dir.getPath()
						+ " was finished by "
						+ formatUser(direvent.getUser())
						+ " "
						+ racers.size()
						+ " users participated");
				for (Iterator iter = racers.iterator(); iter.hasNext();) {
					UploaderPosition stat = (UploaderPosition) iter.next();
					String str1;
					try {
						str1 =
							formatUser(
								_cm.getUsermanager().getUserByName(
									stat.getUsername()));
					} catch (NoSuchUserException e2) {
						continue;
					} catch (IOException e2) {
						logger.log(Level.SEVERE, "Error reading userfile", e2);
						continue;
					}
					say(
						str1
							+ " ["
							+ stat.getFiles()
							+ "f/"
							+ Bytes.formatBytes(stat.getBytes())
							+ "]");
				}
			} else if (sfvfile.size() >= 4) {
				if (sfvfile.finishedFiles() == halfway) {
					say("[halfway] " + dir.getPath() + " has reached halfway");
					for (Iterator iter =
						topFileUploaders2(sfvfile.getFiles()).iterator();
						iter.hasNext();
						) {
						UploaderPosition stat = (UploaderPosition) iter.next();
						String str1;
						try {
							str1 =
								formatUser(
									_cm.getUsermanager().getUserByName(
										stat.getUsername()));
						} catch (NoSuchUserException e2) {
							continue;
						} catch (IOException e2) {
							logger.log(
								Level.SEVERE,
								"Error reading userfile",
								e2);
							continue;
						}
						say(
							str1
								+ " ["
								+ stat.getFiles()
								+ "f/"
								+ Bytes.formatBytes(stat.getBytes())
								+ "]");
					}
				}
			}

		} else if (direvent.getCommand().equals("RMD")) {
			say(
				"[deldir] "
					+ direvent.getDirectory().getPath()
					+ " was deleted by "
					+ formatUser(direvent.getUser()));
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
				stat = new UploaderPosition(username, file.length(), 1);
				ret.add(stat);
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
			}
		}
		Collections.sort(ret);
		return ret;
	}
}
