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

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.text.WordUtils;

/**
 * @author djb61
 * @version $Id$
 */
public class OutputWriter {

	private static final String LINESEP = System.getProperty("line.separator");

	private BlowfishManager _cipher;

	private boolean _blowfishEnabled;

	private SiteBot _bot;

	private String _output;

	private int _maxOutputLen;

	protected OutputWriter() {
		// Empty constructor for extending classes
	}

	public OutputWriter(SiteBot bot, String output, BlowfishManager cipher) {
		_bot = bot;
		_output = output;
		_cipher = cipher;
		_blowfishEnabled = _bot.getConfig().getBlowfishEnabled();
		_maxOutputLen = getMaxLineLength();
	}

	public synchronized void sendMessage(String message) {
		for (String line : splitLines(message)) {
			if (_blowfishEnabled) {
				// Check if we have a valid cipher before proceeding, this is to cover
				// the case where the OutputWriter is for a private message to a user
				// yet they don't have a blowfish key available. This is possible since
				// they could've initiated the command using blowfish in a channel. If
				// this is the case just skip the output to them.
				if (_cipher != null) {
					_bot.sendMessage(_output, _cipher.encrypt(line));
				}
			}
			else {
				_bot.sendMessage(_output, line);
			}
		}
	}

	public void reload() {
		_blowfishEnabled = _bot.getConfig().getBlowfishEnabled();
		_maxOutputLen = getMaxLineLength();
	}

	protected void updateCipher(BlowfishManager cipher) {
		_cipher = cipher;
	}

	protected String getDestination() {
		return _output;
	}

	private String[] splitLines(String message) {
		if (message.length() > _maxOutputLen) {
			return StringUtils.split(WordUtils.wrap(message, _maxOutputLen, LINESEP, true), LINESEP);
		}
		return new String[] { message };
	}

	private int getMaxLineLength() {
		int maxLen = _bot.getConfig().getMaxLineLength() - _bot.getHostMask().length() - _output.length() - 14;
		if (_blowfishEnabled) {
			maxLen = ((maxLen / 12) * 8) - 4;
		}

		return maxLen;
	}
}
