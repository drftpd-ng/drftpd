package net.sf.drftpd.event.listeners;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.event.DirectoryFtpEvent;
import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.irc.IRCListener;
import net.sf.drftpd.master.ConnectionManager;

import org.apache.log4j.Logger;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @version $Id: PreTime.java,v 1.10 2004/01/13 00:38:55 mog Exp $
 */
public class PreTime implements FtpListener {
	private SiteBot _siteBot;
	private IRCListener _irc;
	private static final Logger logger = Logger.getLogger(PreTime.class);
	private ConnectionManager _cm;
	private ArrayList datedDirs;
	private String prebot;

	public PreTime() throws FileNotFoundException, IOException {
		super();
	}

	public void actionPerformed(Event event) {
		if ("RELOAD".equals(event.getCommand())) {
			reload();
			return;
		}
		if (!(event instanceof DirectoryFtpEvent))
			return;
		DirectoryFtpEvent dfe = (DirectoryFtpEvent) event;
		if(!getConnectionManager().getConfig().checkDirLog(dfe.getUser(), dfe.getDirectory())) {
			return;
		}
		try {
			if ( dfe.getCommand().startsWith("MKD") ) {
				String release[] = dfe.getDirectory().getPath().split("/");
				String releaseName;
				if ( isDatedDir(release[1]) ) {
					releaseName = release[3];
					if ( release.length > 4 ) return;  // CD1 || CD2 type directories
				}
				else {
					releaseName = release[2];
					if ( release.length > 3 ) return; // CD1 || CD2 type directories
				}
				if ( releaseName == null ) return; // DatedDir section created date dir
				
				_irc.getIRCConnection().sendCommand(
					new MessageCommand(getPreBot(),"!pred " + releaseName));
			}
		}
		catch (ArrayIndexOutOfBoundsException ex) {
			// do nothing just ignore, it's a directory created in /
		}
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}


	public void init(ConnectionManager mgr) {
		_cm = mgr;
		reload();
	}
	
	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("pretime.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		prebot = props.getProperty("prebot");
		if ( prebot == null ) {
			throw new FatalException("prebot not set in prebot.conf");
		}
		datedDirs = new ArrayList();
		for (int i = 1;; i++) {
			String temp = props.getProperty("DatedDir." + i);
			if ( temp == null )
				break;
			datedDirs.add(temp);
		}

		if (_siteBot != null) {
			_siteBot.disable();
		}
		try {
			_irc =
				(IRCListener) _cm.getFtpListener(IRCListener.class);
			_siteBot = new SiteBot(_irc, this);
		} catch (ObjectNotFoundException e1) {
			logger.warn("Error loading sitebot component", e1);
		}
	}

	public String getPreBot() {
		return prebot;
	}

	public boolean isDatedDir(String section) {
		for (Iterator iter = datedDirs.iterator(); iter.hasNext();) {
			if ( ((String) iter.next()).equals(section) )
				return true;
		}
		return false;
	}

	class SiteBot extends GenericCommandAutoService {

		//private static final Logger logger = Logger.getLogger(SiteBot.class);

		private IRCListener irc;

		private PreTime parent;

		protected SiteBot(IRCListener irc, PreTime parent) {
			super(irc.getIRCConnection());
			this.irc = irc;
			this.parent = parent;
		}

		protected void updateCommand(InCommand command) {
			if (!(command instanceof MessageCommand))
				return;
			MessageCommand msgc = (MessageCommand) command;
			if ( msgc.getSource().getNick().equals(parent.getPreBot()) ) {
				if ( msgc.isPrivateToUs(irc.getIRCConnection().getClientState())) {
					String msg[] = msgc.getMessage().split(" ");
					if (msg[0].equals("!preds")) {
						String releaseName = msg[1];
						int releaseTime = Integer.parseInt(msg[2]);
						int weeks = releaseTime / 604800;
						releaseTime = releaseTime % 604800;
						int days = releaseTime / 86400;
						releaseTime = releaseTime % 86400;
						int hours = releaseTime / 3600;
						releaseTime = releaseTime % 3600;
						int minutes = releaseTime / 60;
						int seconds = releaseTime % 60;
						String time = "-=PRETiME=- " + releaseName + " was pred ";
						if ( weeks != 0 ) time = time + weeks + " weeks ";
						if ( days != 0 ) time = time + days + " days ";
						if ( hours != 0 ) time = time + hours + " hours ";
						if ( minutes != 0 ) time = time + minutes + " minutes ";
						if ( seconds != 0 ) time = time + seconds + " seconds ";
						irc.say(time + "ago");
					}
				}
			}
		}
	}
}
