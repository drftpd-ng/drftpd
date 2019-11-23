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

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


import java.io.BufferedWriter;

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public class OutputThread extends Thread {

	private static final Logger logger = LogManager.getLogger(OutputThread.class);
	private SiteBot _bot = null;
	private Queue _outQueue = null;

	/**
	 * Constructs an OutputThread for the underlying SiteBot.  All messages
	 * sent to the IRC server are sent by this OutputThread to avoid hammering
	 * the server.  Messages are sent immediately if possible.  If there are
	 * multiple messages queued, then there is a delay imposed.
	 * 
	 * @param bot The underlying SiteBot instance.
	 * @param outQueue The Queue from which we will obtain our messages.
	 */
	OutputThread(SiteBot bot, Queue outQueue) {
		_bot = bot;
		_outQueue = outQueue;
		this.setName(bot.getBotName() + "-OutputThread");
	}


	/**
	 * A static method to write a line to a BufferedOutputStream and then pass
	 * the line to the log method of the supplied SiteBot instance.
	 * 
	 * @param bot The underlying SiteBot instance.
	 * @param out The BufferedOutputStream to write to.
	 * @param line The line to be written. "\r\n" is appended to the end.
	 * @param encoding The charset to use when encoing this string into a
	 *                 byte array.
	 */
	static void sendRawLine(SiteBot bot, BufferedWriter bwriter, String line) {
		if (line.length() > bot.getConfig().getMaxLineLength() - 2) {
			line = line.substring(0, bot.getConfig().getMaxLineLength() - 2);
		}
		synchronized(bwriter) {
			try {
				bwriter.write(line + "\r\n");
				bwriter.flush();
                logger.debug(">>>{}", line);
			}
			catch (Exception e) {
				// Silent response - just lose the line.
			}
		}
	}


	/**
	 * This method starts the Thread consuming from the outgoing message
	 * Queue and sending lines to the server.
	 */
	public void run() {
		try {
			boolean running = true;
			while (running) {
				if (_bot.getMessageDelay() > 0) {
					// Small delay to prevent spamming of the channel
					Thread.sleep(_bot.getMessageDelay());
				}

				String line = _outQueue.next();
				if (line != null) {
					_bot.sendRawLine(line);
				}
				else {
					logger.debug("Got null line");
					running = false;
				}
			}
		}
		catch (InterruptedException e) {
			// Just let the method return naturally...
		}
	}
}
