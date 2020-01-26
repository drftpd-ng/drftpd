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
package org.drftpd.commands.speedtest.net;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.GlobalContext;
import org.drftpd.exceptions.SlaveUnavailableException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.speedtest.net.common.SpeedTestInfo;
import org.drftpd.protocol.speedtest.net.common.async.AsyncResponseSpeedTestInfo;
import org.drftpd.protocol.speedtest.net.master.SpeedTestIssuer;
import org.drftpd.slave.RemoteIOException;

import java.util.HashMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;

/**
 * @author scitz0
 */
public class SpeedTestCallable implements Callable<SpeedTestInfo> {

	private static final Logger logger = LogManager.getLogger(SpeedTestCallable.class);

	private RemoteSlave _rslave;
	private HashMap<String, SpeedTestServer> _testServers;

	public SpeedTestCallable(RemoteSlave rslave, HashMap<String, SpeedTestServer> testServers) {
		_rslave = rslave;
		_testServers = testServers;
	}

	@Override
	public SpeedTestInfo call() throws Exception {
		// Send speedtest request to slave
		SpeedTestInfo result;
		try {
			String testServerURLs = "";
			for (String url : _testServers.keySet()) {
				testServerURLs += " " + url;
			}
			String index = getSpeedTestIssuer().issueSpeedTestToSlave(_rslave, testServerURLs.trim());
			result = fetchSpeedTestInfoFromIndex(_rslave, index);
		} catch (SlaveUnavailableException e) {
			throw new ExecutionException(_rslave.getName() + " went offline trying to run speedtest!", e);
		} catch (RemoteIOException e) {
			throw new ExecutionException("Error: " + e.getMessage(), e);
		}
		result.setSlaveName(_rslave.getName());
		return result;
	}

	private SpeedTestInfo fetchSpeedTestInfoFromIndex(RemoteSlave rslave, String index) throws RemoteIOException, SlaveUnavailableException {
		return ((AsyncResponseSpeedTestInfo) rslave.fetchResponse(index)).getSpeedTest();
	}

	private SpeedTestIssuer getSpeedTestIssuer() {
		return (SpeedTestIssuer) GlobalContext.getGlobalContext().getSlaveManager().getProtocolCentral().getIssuerForClass(SpeedTestIssuer.class);
	}
}
