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
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;

import org.apache.log4j.Logger;
import org.drftpd.plugins.SiteBot;

import f00f.net.irc.martyr.GenericCommandAutoService;
import f00f.net.irc.martyr.InCommand;
import f00f.net.irc.martyr.commands.MessageCommand;

/**
 * @author zubov
 * @version $Id: PreTime.java,v 1.17 2004/04/25 17:46:15 mog Exp $
 */
public class PreTime implements FtpListener {

	public static class PreSiteBot extends GenericCommandAutoService {

		private SiteBot _irc;

		private PreTime _parent;

		protected PreSiteBot(SiteBot irc, PreTime parent) {
			super(irc.getIRCConnection());
			_irc = irc;
			_parent = parent;
		}

		protected void updateCommand(InCommand command) {
			if (!(command instanceof MessageCommand))
				return;
			MessageCommand msgc = (MessageCommand) command;
			if (msgc.getSource().getNick().equals(_parent.getPreBot())) {
				if (msgc
					.isPrivateToUs(_irc.getIRCConnection().getClientState())) {
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
						String time =
							"-=PRETiME=- " + releaseName + " was pred ";
						if (weeks != 0)
							time = time + weeks + " weeks ";
						if (days != 0)
							time = time + days + " days ";
						if (hours != 0)
							time = time + hours + " hours ";
						if (minutes != 0)
							time = time + minutes + " minutes ";
						if (seconds != 0)
							time = time + seconds + " seconds ";
						//TODO send to originating destination channel, must store a cookie either locally or in message
						_irc.say(null, time + "ago");
					}
				}
			}
		}
	}
	private static final Logger logger = Logger.getLogger(PreTime.class);
	private ConnectionManager _cm;
	private SiteBot _irc;
	private PreSiteBot _siteBot;
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
		if (!getConnectionManager()
			.getConfig()
			.checkDirLog(dfe.getUser(), dfe.getDirectory())) {
			return;
		}
		try {
			if (dfe.getCommand().startsWith("MKD")) {
				String release[] = dfe.getDirectory().getPath().split("/");
				String releaseName;
				if (isDatedDir(release[1])) {
					releaseName = release[3];
					if (release.length > 4)
						return; // CD1 || CD2 type directories
				} else {
					releaseName = release[2];
					if (release.length > 3)
						return; // CD1 || CD2 type directories
				}
				if (releaseName == null)
					return; // DatedDir section created date dir

				_irc.getIRCConnection().sendCommand(
					new MessageCommand(getPreBot(), "!pred " + releaseName));
			}
		} catch (ArrayIndexOutOfBoundsException ex) {
			// do nothing just ignore, it's a directory created in /
		}
	}

	public ConnectionManager getConnectionManager() {
		return _cm;
	}

	public String getPreBot() {
		return prebot;
	}

	public void init(ConnectionManager mgr) {
		_cm = mgr;
		reload();
	}

	public boolean isDatedDir(String section) {
		for (Iterator iter = datedDirs.iterator(); iter.hasNext();) {
			if (((String) iter.next()).equals(section))
				return true;
		}
		return false;
	}

	private void reload() {
		Properties props = new Properties();
		try {
			props.load(new FileInputStream("conf/pretime.conf"));
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		prebot = FtpConfig.getProperty(props, "prebot");
		if (prebot == null) {
			throw new FatalException("prebot not set in prebot.conf");
		}
		datedDirs = new ArrayList();
		for (int i = 1;; i++) {
			String temp = null;
			try {
				temp = FtpConfig.getProperty(props, "DatedDir." + i);
			} catch (NullPointerException e2) {
				break;
			}
			datedDirs.add(temp);
		}

		if (_siteBot != null) {
			_siteBot.disable();
		}
		try {
			_irc = (SiteBot) _cm.getFtpListener(PreSiteBot.class);
			_siteBot = new PreSiteBot(_irc, this);
		} catch (ObjectNotFoundException e1) {
			logger.warn("Error loading sitebot component", e1);
		}
	}

	public void unload() {

	}

}
