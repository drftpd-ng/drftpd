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
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.event.UserEvent;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.usermanager.NoSuchUserException;
import net.sf.drftpd.master.usermanager.User;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.slave.SlaveStatus;
import f00f.net.irc.martyr.AutoJoin;
import f00f.net.irc.martyr.AutoReconnect;
import f00f.net.irc.martyr.AutoRegister;
import f00f.net.irc.martyr.AutoResponder;
import f00f.net.irc.martyr.Debug;
import f00f.net.irc.martyr.IRCConnection;
import f00f.net.irc.martyr.clientstate.Channel;
import f00f.net.irc.martyr.clientstate.ClientState;
import f00f.net.irc.martyr.commands.MessageCommand;
import f00f.net.irc.martyr.util.FullNick;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class IRCListener implements FtpListener {
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
	public IRCListener(ConnectionManager cm, Properties cfg)
		throws UnknownHostException, IOException {

		Debug.setDebugLevel(Debug.FAULT);
		_cm = cm;

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
		_ircConnection.addCommandObserver(new Observer() {

			public void update(Observable observer, Object updated) {
				if (updated instanceof MessageCommand) {
					MessageCommand msgc = (MessageCommand) updated;
					String message = msgc.getMessage();
					FullNick source = msgc.getSource();

					if (message.equals("!help")) {
						_ircConnection.sendCommand(
							new MessageCommand(
								_channelName,
								"Available commands: !bw !slaves"));
					} else if (message.equals("!bw")) {
						SlaveStatus status =
							_cm.getSlavemanager().getAllStatus();

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

						_ircConnection.sendCommand(
							new MessageCommand(
								_channelName,
								"[conns total: "
									+ total
									+ " idle: "
									+ idlers
									+ "] [xfers total: "
									+ status.getTransfers()
									+ ", "
									+ Bytes.formatBytes(status.getThroughput())
									+ "/s] [xfers up "
									+ status.getTransfersReceiving()
									+ ", "
									+ Bytes.formatBytes(status.getThroughputReceiving())
									+ "/s] [xfers down "
									+ status.getTransfersSending()
									+ ", "
									+ Bytes.formatBytes(status.getThroughputSending())
									+ "/s]  [space "
									+ Bytes.formatBytes(status.getDiskSpaceAvailable())
									+ "/"
									+ Bytes.formatBytes(status.getDiskSpaceCapacity())
									+ "]"));
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
								statusString =
									"[xfers total: "
										+ status.getTransfers()
										+ " "
										+ Bytes.formatBytes(status.getThroughput())
										+ "/s] [xfers up "
										+ status.getTransfersReceiving()
										+ ", "
										+ Bytes.formatBytes(status.getThroughputReceiving())
										+ "/s] [xfers down "
										+ status.getTransfersSending()
										+ ", "
										+ Bytes.formatBytes(status.getThroughputSending())
										+ "/s] [space free: "
										+ Bytes.formatBytes(status.getDiskSpaceAvailable())
										+ "/"
										+ Bytes.formatBytes(status.getDiskSpaceCapacity())
										+ "]";
							} catch (RemoteException e) {
								rslave.handleRemoteException(e);
								statusString = "offline";
							} catch (NoAvailableSlaveException e) {
								statusString = "offline";
							}
							say(
								"[slaves] "
									+ rslave.getName()
									+ " "
									+ statusString);
						}
					}
				}
			}

		});
		System.out.println(
			"IRCListener: connecting to " + _server + ":" + _port);
		_ircConnection.connect(_server, _port);
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
		if(event instanceof DirectoryFtpEvent) {
			actionPerformed((DirectoryFtpEvent)event);
			return;
		}
		if (event instanceof UserEvent) {
			actionPerformed((UserEvent) event);
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
			SlaveEvent sevent = (SlaveEvent)event;
			say("[slave] "+sevent.getRSlave().getName()+" just went offline");
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
			//TODO new racer
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

			//TODO ceil halfway
			int halfway = sfvfile.size() / 2;
			///// start ///// start ////
			
			//check if new racer
			String username = direvent.getUser().getUsername();
			if(finishedFiles != 1) {
				for (Iterator iter = sfvfile.getFiles().iterator(); iter.hasNext();) {
					LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
					if(file.getOwner().equals(username)) break;
					if(!iter.hasNext()) {
						say(dir.getPath()+" embraces "+formatUser(direvent.getUser()));
					}
				}
			}

			if (finishedFiles == sfvfile.size()) {
				Collection racers = topFileUploaders2(sfvfile.getFiles());
				say(
					"[complete] "
						+ dir.getPath()
						+ " was finished by "
						+ formatUser(direvent.getUser())+" "+racers.size()+" users participated");
				for (Iterator iter =
					racers.iterator();
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
		} else if(direvent.getCommand().equals("WIPE")) {
			if(direvent.getDirectory().isDirectory()) {
				say(
				"[wipe] "
					+ direvent.getDirectory().getPath()
					+ " was wiped by "
					+ formatUser(direvent.getUser()));
			}
		}

	}
	public void actionPerformed(UserEvent event) {
		//_ircConnection.sendCommand(
		//	new MessageCommand(_channelName, event.toString()));
	}
	public static Collection topFileUploaders2(Collection files) {
		ArrayList ret = new ArrayList();
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String username = file.getOwner();

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
