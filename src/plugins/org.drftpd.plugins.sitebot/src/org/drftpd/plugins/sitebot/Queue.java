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

import java.util.concurrent.LinkedBlockingQueue;

/**
 * @author Modified from PircBot by Paul James Mutton, http://www.jibble.org/
 * @author djb61
 * @version $Id$
 */
public class Queue {

	private LinkedBlockingQueue<String> _queue;

	/**
	 * Constructs a Queue object of Integer.MAX_VALUE size.
	 */
	public Queue() {
		_queue = new LinkedBlockingQueue<>();
	}

	/**
	 * Adds a String to the Queue.
	 *
	 * @param s The String to be added to the Queue.
	 */
	public void add(String s) {
		_queue.add(s);
	}

	/**
	 * Returns the String at the front of the Queue.  This
	 * String is then removed from the Queue.  If the Queue
	 * is empty, then this method shall block until there
	 * is a String in the Queue to return.
	 *
	 * @return The next item from the front of the queue.
	 */
	public String next() {

		String next = null;
		try {
			next = _queue.take();
		} catch (InterruptedException e) {
			// do nothing
		}
		return next;
	}

	/**
	 * Returns true if the Queue is not empty.  If another
	 * Thread empties the Queue before <b>next()</b> is
	 * called, then the call to <b>next()</b> shall block
	 * until the Queue has been populated again.
	 *
	 * @return True only if the Queue not empty.
	 */
	public boolean hasNext() {
		return !_queue.isEmpty();
	}

	/**
	 * Clears the contents of the Queue.
	 */
	public void clear() {
		_queue.clear();
	}
}
