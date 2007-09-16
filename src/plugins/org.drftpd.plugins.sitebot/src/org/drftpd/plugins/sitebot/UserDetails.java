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
package org.drftpd.plugins.sitebot;

import java.util.ArrayList;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.dynamicdata.Key;
import org.drftpd.dynamicdata.KeyNotFoundException;
import org.drftpd.usermanager.NoSuchUserException;
import org.drftpd.usermanager.User;
import org.drftpd.usermanager.UserFileException;

/**
 * @author djb61
 * @version $Id$
 */
public class UserDetails {

	private static final Logger logger = Logger.getLogger(UserDetails.class);

	public static final Key BLOWKEY = new Key(UserDetails.class,
			"blowfishkey", String.class);

	private SiteBot _bot;

	private Blowfish _cipher = null;

	private String _blowKey = null;

	private String _ftpUser = null;

	private String _ident;

	private String _nick;

	private OutputWriter _writer;

	private ArrayList<ServiceCommand> _commandSessions = new ArrayList<ServiceCommand>();

	public UserDetails(String nick, String ident, SiteBot bot) {
		_bot = bot;
		_ident = ident;
		_nick = nick;
		_writer = new OutputWriter(_bot, _nick, _cipher, _bot.getConfig().getBlowfishEnabled());
		getDetailsFromDB();
	}

	protected String getFtpUser() {
		// If we don't know the ftp username then check again as they may have updated the
		// ident in the userfile since we last tried.
		if (_ftpUser == null) {
			getDetailsFromDB();
		}
		return _ftpUser;
	}

	protected Blowfish getBlowCipher() {
		// If we don't have a cipher for this user then check again as they may have set
		// one on site since we last checked.
		if (_cipher == null) {
			getDetailsFromDB();
		}
		return _cipher;
	}

	protected void setBlowCipher(String blowKey) {
		_cipher = new Blowfish(blowKey);
		_blowKey = blowKey;
		// If we know who the user is then update their userfile with the new key
		if (_ftpUser != null) {
			try {
				User user = GlobalContext.getGlobalContext().getUserManager().getUserByName(_ftpUser);
				setUserBlowKey(user);
			} catch (NoSuchUserException e) {
				// do nothing
			} catch (UserFileException e) {
				logger.warn("Error loading userfile for "+_ftpUser,e);
			}
		}
		// Update cipher in the users OutputWriter
		_writer.updateCipher(_cipher);
	}

	private void getDetailsFromDB() {
		try {
			User user;
			// Check if we need to identify the user by ident or if we know their username
			if (_ftpUser == null) {
				user = GlobalContext.getGlobalContext().getUserManager().getUserByIdent(_ident);
				_ftpUser = user.getName();
			} else {
				user = GlobalContext.getGlobalContext().getUserManager().getUserByName(_ftpUser);
			}
			// If we know of a blowfish key for this user then set this in their userfile
			// now we have identified them, if we don't and they have a key set in the
			// userfile then use that key, if we still can't find a key then try DH1080 if
			// appropriate.
			if (_blowKey != null) {
				setUserBlowKey(user);
			} else {
				try {
					String userKey = (String)user.getKeyedMap().getObject(BLOWKEY);
					if (!userKey.equals("")) {
						setBlowCipher(userKey);
					} else {
						_bot.initiateDH1080(_nick);
					}
				} catch (KeyNotFoundException e1) {
					// Means this user has never set a blowfish key, is safe to proceed
				}
			}
		} catch (NoSuchUserException e) {
			//do nothing
		} catch (UserFileException e) {
			logger.warn("Error loading userfile for "+_ftpUser,e);
		}
	}

	private void setUserBlowKey(User user) {
		user.getKeyedMap().setObject(BLOWKEY, _blowKey);
		user.commit();
	}

	protected void setNick(String nick) {
		_nick = nick;
	}

	protected OutputWriter getOutputWriter() {
		return _writer;
	}

	protected synchronized ServiceCommand getCommandSession(ArrayList<OutputWriter> outputs) {
		ServiceCommand newSession = new ServiceCommand(_bot, outputs, this, _ident);
		_commandSessions.add(newSession);
		return newSession;
	}

	protected synchronized void removeCommandSession(ServiceCommand session) {
		_commandSessions.remove(session);
	}

	public String getNick() {
		return _nick;
	}
}
