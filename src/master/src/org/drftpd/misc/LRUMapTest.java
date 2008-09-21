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
package org.drftpd.misc;

import java.util.Map;
import java.util.Map.Entry;

import org.apache.log4j.Logger;

import junit.framework.TestCase;

/**
 * @author fr0w
 * @version $Id$
 */
public class LRUMapTest extends TestCase {

	private static final int MAX_SIZE = 10;
	private LRUMap<Integer, Integer> _lruMap = new LRUMap<Integer, Integer>(MAX_SIZE);

	private static final Logger logger = Logger.getLogger(LRUMapTest.class);
			
	protected void setUp() {
		// fill the map.
		for (int i = 1; i<=MAX_SIZE; i++) {
			_lruMap.put(i, i);
		}
	}
	
	public void dumpMap(Map<Integer,Integer> m) {
		logger.debug("Dumping the map");
		for (Entry<Integer, Integer> entry : _lruMap.entrySet()) {
			logger.debug(entry.getKey()+","+entry.getValue());
		}
	}
	
	public void testInitialInsertion() {
		logger.debug("Running InitialInsertionTest");
		assertTrue(_lruMap.containsKey(MAX_SIZE));
	}
	
	public void testInsertion() {
		logger.debug("Running InsertionTest");
		dumpMap(_lruMap);
		_lruMap.put(MAX_SIZE+1, MAX_SIZE+1);
		dumpMap(_lruMap);
		assertFalse(_lruMap.containsKey(1));
		assertTrue(_lruMap.containsKey(MAX_SIZE+1));
	}
	
}
