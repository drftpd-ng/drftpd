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

/**
 * @author djb61
 * @version $Id$
 */
public class OutputWriter {

	private Blowfish _cipher;

	private boolean _blowfishEnabled;

	private SiteBot _bot;

	private String _output;

	protected OutputWriter() {
		// Empty constructor for extending classes
	}

	public OutputWriter(SiteBot bot, String output, Blowfish cipher, boolean blowfishEnabled) {
		_bot = bot;
		_output = output;
		_cipher = cipher;
		_blowfishEnabled = blowfishEnabled;
	}

	public void sendMessage(String message) {
		if (_blowfishEnabled) {
			// Check if we have a valid cipher before proceeding, this is to cover
			// the case where the OutputWriter is for a private message to a user
			// yet they don't have a blowfish key available. This is possible since
			// they could've initiated the command using blowfish in a channel. If
			// this is the case just skip the output to them.
			if (_cipher != null) {
				_bot.sendMessage(_output, _cipher.encrypt(message));
			}
		}
		else {
			_bot.sendMessage(_output, message);
		}
	}

	protected void updateCipher(Blowfish cipher) {
		_cipher = cipher;
	}
	
	protected String getDestination() {
		return _output;
	}
}
