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
package org.drftpd.tests;

import junit.framework.TestCase;
import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPClientConfig;
import org.apache.commons.net.ftp.FTPReply;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.junit.Assert;

import java.util.ArrayList;
import java.util.Collections;

/**
 * This a Stress TestCase for the ConnectionManager ThreadPool.<br>
 * It tries to connect to 'localhost:2121' and hammer the daemon wiyh 100 connections.<br>
 * You can change those settings by simple code changes, not going to provide configuration files for this.<br>
 * The code depends on Jakarta Commons, check it out in <link>http://jakarta.apache.org/commons/net/</link>
 * @author fr0w
 * @version $Id$
 */
public class ConnectionStressTest extends TestCase {
	
	private static final Logger logger = LogManager.getLogger(ConnectionStressTest.class);
	
	@SuppressWarnings("unused")
	private int failures = 0;
	private int success = 0;
	
	private ArrayList<Thread> list = new ArrayList<>();
	
	public ConnectionStressTest(String fName) {
		super(fName);
	}
	
	public void testStress() {
		int i = 0;
		for (; i < 200; i++) {
			Thread t = new Thread(new DummyClient(this));
			
			list.add(t);
			
			t.start();
			t.setName("DummyClient-"+i);
            logger.debug("Launching DummyClient #{}", i);
			
			try {
				Thread.sleep(100); //give the daemon some time.	
			} catch (InterruptedException e) {
				logger.fatal(e,e);
			} 
		}

		Assert.assertTrue(success == i); // means that every attemp was successful when connecting

		Collections.reverse(list); // must reverse the order in order to iterate thru the first client firstly.
		
		int dead = 0;
		for (Thread t : list) {
			while (t.isAlive()) {
				// shotdown gracefully.
			}
			dead += 1;
		}

		Assert.assertTrue(dead == success); // all threads were finalized.
	}
	
	public void addFailure() {
		failures += 1;
	}
	
	public void addSuccess() {
		success += 1;
	}	
}

class DummyClient implements Runnable {
	private static FTPClientConfig ftpConfig = new FTPClientConfig(FTPClientConfig.SYST_UNIX);
	private static final Logger logger = LogManager.getLogger(DummyClient.class);
	
	private ConnectionStressTest _sc;
	
	public DummyClient(ConnectionStressTest sc) {
		_sc = sc;
	}
	
	public void run() {
		try {
			FTPClient c = new FTPClient();
			c.configure(ftpConfig);

			logger.debug("Trying to connect");
			c.connect("127.0.0.1", 21211);
			logger.debug("Connected");

			c.setSoTimeout(5000);

			if(!FTPReply.isPositiveCompletion(c.getReplyCode())) {
				logger.debug("Houston, we have a problem. D/C");
				c.disconnect();
				throw new Exception();
			}

			if (c.login("drftpd", "drftpd")) {
				logger.debug("Logged-in, now waiting 5 secs and kill the thread.");
				_sc.addSuccess();
				Thread.sleep(5000);
				c.disconnect();
			} else {
				logger.debug("Login failed, D/C!");
				throw new Exception();
			}		
		} catch (Exception e) {
			logger.debug(e,e);
			_sc.addFailure();
		} 
		
		logger.debug("exiting");
	}	
}

