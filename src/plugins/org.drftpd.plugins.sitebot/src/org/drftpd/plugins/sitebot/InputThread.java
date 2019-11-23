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


import java.io.*;
import java.net.Socket;
import java.util.StringTokenizer;

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public class InputThread extends Thread {

	private static final Logger logger = LogManager.getLogger(InputThread.class);

	private SiteBot _bot = null;
	private Socket _socket = null;
	private BufferedReader _breader = null;
	private BufferedWriter _bwriter = null;
	private boolean _isConnected = true;
	private boolean _disposed = false;

	/**
	 * The InputThread reads lines from the IRC server and allows the
	 * SiteBot to handle them.
	 *
	 * @param bot An instance of the underlying SiteBot.
	 * @param breader The BufferedReader that reads lines from the server.
	 * @param bwriter The BufferedWriter that sends lines to the server.
	 */
	InputThread(SiteBot bot, Socket socket, BufferedReader breader, BufferedWriter bwriter) {
		_bot = bot;
		_socket = socket;
		_breader = breader;
		_bwriter = bwriter;
		this.setName(bot.getBotName() + "-InputThread");
	}


	/**
	 * Sends a raw line to the IRC server as soon as possible, bypassing the
	 * outgoing message queue.
	 *
	 * @param line The raw line to send to the IRC server.
	 */
	void sendRawLine(String line) {
		OutputThread.sendRawLine(_bot, _bwriter, line);
	}


	/**
	 * Returns true if this InputThread is connected to an IRC server.
	 * The result of this method should only act as a rough guide,
	 * as the result may not be valid by the time you act upon it.
	 * 
	 * @return True if still connected.
	 */
	boolean isConnected() {
		return _isConnected;
	}


	/**
	 * Called to start this Thread reading lines from the IRC server.
	 * When a line is read, this method calls the handleLine method
	 * in the SiteBot, which may subsequently call an 'onXxx' method
	 * in the SiteBot subclass.  If any subclass of Throwable (i.e.
	 * any Exception or Error) is thrown by your method, then this
	 * method will print the stack trace to the standard output.  It
	 * is probable that the SiteBot may still be functioning normally
	 * after such a problem, but the existance of any uncaught exceptions
	 * in your code is something you should really fix.
	 */
	public void run() {
		try {
			boolean running = true;
			while (running) {
				try {
					String line = null;
					while ((line = _breader.readLine()) != null) {
						try {
							_bot.handleLine(line);
						}
						catch (Throwable t) {
							// Stick the whole stack trace into a String so we can output it nicely.
							StringWriter sw = new StringWriter();
							PrintWriter pw = new PrintWriter(sw);
							t.printStackTrace(pw);
							pw.flush();
							StringTokenizer tokenizer = new StringTokenizer(sw.toString(), "\r\n");
							logger.warn("### There is a bug in the SiteBot code which");
							logger.warn("### allowed an uncaught Exception or Error to propagate in the");
							logger.warn("### code. It may be possible for the SiteBot to continue operating");
							logger.warn("### normally. Here is the stack trace that was produced: -");
							logger.warn("### ");
							while (tokenizer.hasMoreTokens()) {
                                logger.warn("### {}", tokenizer.nextToken());
							}

						}
					}
					// The server must have disconnected us.
					running = false;
				}
				catch (InterruptedIOException iioe) {
					// This will happen if we haven't received anything from the server for a while.
					// So we shall send it a ping to check that we are still connected.
					this.sendRawLine("PING " + (System.currentTimeMillis() / 1000));
					// Now we go back to listening for stuff from the server...
				}
			}
		}
		catch (Exception e) {
			// Do nothing.
		}

		// If we reach this point, then we must have disconnected.
		try {
			_socket.close();
		}
		catch (Exception e) {
			// Just assume the socket was already closed.
		}

		if (!_disposed) {
			logger.info("*** Disconnected.");        
			_isConnected = false;
			_bot.onDisconnect();
		}

	}


	/**
	 * Closes the socket without onDisconnect being called subsequently.
	 */
	public void dispose() {
		try {
			_disposed = true;
			_socket.close();
		}
		catch (Exception e) {
			// Do nothing.
		}
	}    
}
