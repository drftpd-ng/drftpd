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

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.SFVFile;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.FtpEvent;
import net.sf.drftpd.event.FtpListener;
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
		
		Debug.setDebugLevel(Debug.NORMAL);
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
		new AutoRegister(_ircConnection, cfg.getProperty("irc.nick"), cfg.getProperty("irc.user"), cfg.getProperty("irc.name"));
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
								"Available commands: !bw !df"));
					} else if (message.equals("!bw")) {
						//[bw] i [ total: 6 of 20 / 885kb/s ] - [ up: 2 / 485kb/s | dn: 2 / 400kb/s | idle: 2 ]
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
								"[conns total: "+total+" idle: "
									+ idlers
									+ "] [xfers total: "
									+ status.getTransfers()
									+ " "
									+ status.getThroughput()
									+ "b/s] [xfers up "
									+ status.getTransfersReceiving()
									+ " "
									+ status.getThroughputReceiving()
									+ "b/s] [xfers down "
									+ status.getTransfersSending()
									+ " "
									+ status.getThroughputSending()
									+ "b/s]  [space "+status.getDiskSpaceAvailable()+"/"+status.getDiskSpaceCapacity()+"b]"));
					} else if(message.equals("!slaves")) {
						for (Iterator iter = _cm.getSlavemanager().getSlaves().iterator();
							iter.hasNext();
							) {
							RemoteSlave rslave = (RemoteSlave) iter.next();
							String statusString;
							try {
								SlaveStatus status = rslave.getSlave().getSlaveStatus();
								statusString = "[xfers total: "
									+ status.getTransfers()
									+ " "
									+ status.getThroughput()
									+ "b/s] [xfers up "
									+ status.getTransfersReceiving()
									+ " "
									+ status.getThroughputReceiving()
									+ "b/s] [xfers down "
									+ status.getTransfersSending()
									+ " "
									+ status.getThroughputSending()
									+ "b/s] [space "+status.getDiskSpaceAvailable()+"/"+status.getDiskSpaceCapacity()+"b]";
							} catch (RemoteException e) {
								rslave.handleRemoteException(e);
								statusString = "offline";
							} catch (NoAvailableSlaveException e) {
								statusString = "offline";
							}
							say("[slaves] "+rslave.getName()+ " "+statusString);
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
		return user.getUsername()+"/"+user.getGroup();
	}
	
	/* (non-Javadoc)
	 * @see net.sf.drftpd.event.FtpListener#actionPerformed(net.sf.drftpd.event.FtpEvent)
	 */
	public void actionPerformed(FtpEvent event) {
		//_ircConnection.sendCommand(
		//	new MessageCommand(_channelName, event.toString()));
		logger.log(Level.INFO, event.getCommand());
		if(event.getCommand().equals("MKD")) {
			DirectoryFtpEvent direvent = (DirectoryFtpEvent) event;
			say("[newdir] "+direvent.getDirectory().getName()+" was created by "+formatUser(direvent.getUser()));
			
			
		} else if(event.getCommand().equals("STOR")) {
			DirectoryFtpEvent direvent = (DirectoryFtpEvent) event;
			LinkedRemoteFile dir;
			try {
				dir = direvent.getDirectory().getParentFile();
			} catch (FileNotFoundException e) {
				throw new FatalException(e);
			}
			SFVFile sfvfile;
			try {
				sfvfile = dir.lookupSFVFile(); // throws IOException, ObjectNotFoundException, NoAvailableSlaveException
			} catch (Exception e1) {
				logger.log(Level.SEVERE, "lookupSFVFile failed", e1);
				return;
			}
			int finishedFiles = sfvfile.finishedFiles();
			
			//TODO ceil halfway
			int halfway = sfvfile.size()/2;
			System.out.println("halfway = "+halfway+"/"+sfvfile.size());
			
			if(finishedFiles == sfvfile.size()) {
				say("[complete] "+dir.getName()+" was finished by "+formatUser(direvent.getUser()));
			} else if(sfvfile.size() >= 4) {
				if(sfvfile.finishedFiles() == halfway) {
					say("[halfway] "+dir.getName()+" has reached halfway");
					for (Iterator iter = topFileUploaders2(sfvfile.getFiles()).iterator();
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
							logger.log(Level.SEVERE, "Error reading userfile", e2);
							continue;
						}
						say(str1+" ["+stat.getFiles()+"f/"+stat.getBytes()+"b]"); 
					}
				}
			}
			
			
			
		} else if(event.getCommand().equals("RMD")) {
			DirectoryFtpEvent direvent = (DirectoryFtpEvent) event;
			say("[deldir] "+direvent.getDirectory().getName()+" was deleted by "+formatUser(direvent.getUser()));
		}
	}
	public static Collection topFileUploaders2(Collection files) {
		ArrayList ret = new ArrayList();
		for (Iterator iter = files.iterator(); iter.hasNext();) {
			LinkedRemoteFile file = (LinkedRemoteFile) iter.next();
			String username = file.getOwner();
			
			UploaderPosition stat = null;
			for (Iterator iter2 = ret.iterator(); iter2.hasNext();) {
				UploaderPosition stat2 = (UploaderPosition) iter2.next();
				if(stat2.getUsername().equals(username)) {
					stat = stat2;
					break;
				}
			}
			if(stat == null) {
				stat = new UploaderPosition(username, file.length(), 1); 
			} else {
				stat.updateBytes(file.length());
				stat.updateFiles(1);
			}
		}
		Collections.sort(ret);
		return ret;
	}
}
