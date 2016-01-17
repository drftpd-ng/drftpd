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

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.sitebot.SiteBot;
import org.drftpd.protocol.speedtest.net.common.SpeedTestInfo;
import org.tanesha.replacer.ReplacerEnvironment;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.ResourceBundle;
import java.util.TreeSet;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * @author scitz0
 */
public class SpeedTest extends CommandInterface {
	private static final Logger logger = Logger.getLogger(SpeedTest.class);
	private ResourceBundle _bundle;
	private String _keyPrefix;
	private HashSet<SpeedTestServer> _servers;
	private static final DecimalFormat _numberFormat = new DecimalFormat("#.00");

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
		_servers = SpeedTestUtils.getClosetsServers();
	}

	public CommandResponse doSITE_SPEEDTEST(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		int testServerID = 0;
		String slaveName = args[0];
		boolean allSlaves = slaveName.equals("*");
		boolean listservers = false;

		if (args[0].equals("-refresh")) {
			_servers = SpeedTestUtils.getClosetsServers();
			return new CommandResponse(200, "speedtest.net server list updated!");
		}
		if (args.length == 2 && !allSlaves && args[1].equals("-list")) {
			listservers = true;
		} else if (args.length == 2 && !allSlaves) {
			testServerID = Integer.parseInt(args[1]);
		} else if (args.length != 1) {
			throw new ImproperUsageException();
		}

		if (_servers == null) {
			return new CommandResponse(500, "No test servers cashed, check log for warn message!");
		} else if (_servers.isEmpty()) {
			return new CommandResponse(500, "Could not find any servers, refresh list with -refresh");
		}

		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		ArrayList<RemoteSlave> rslaves = new ArrayList<RemoteSlave>();
		try {
			if (allSlaves) {
				rslaves.addAll(GlobalContext.getGlobalContext().getSlaveManager().getSlaves());
			} else {
				rslaves.add(GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName));
			}
		} catch (ObjectNotFoundException e) {
			return new CommandResponse(500, slaveName + " not found, check spelling!");
		}

		HashMap<String, SpeedTestServer> usedServers = new HashMap<String, SpeedTestServer>();
		HashMap<String, SlaveLocation> slaveLocations = new HashMap<String, SlaveLocation>();

		ExecutorService executor = Executors.newFixedThreadPool(rslaves.size());
		List<Future<SpeedTestInfo>> slaveThreadList = new ArrayList<Future<SpeedTestInfo>>();

		for (RemoteSlave rslave : rslaves) {
			if (!rslave.isOnline()) {
				request.getSession().printOutput(500, rslave.getName() + " is offline, unable to run speed test");
				break;
			}
			HashMap<String, SpeedTestServer> testServers = new HashMap<String, SpeedTestServer>();

			SlaveLocation slaveLocation = SpeedTestUtils.getSlaveLocation(rslave);
			if (slaveLocation.getLatitude() == 0 || slaveLocation.getLongitude() == 0) {
				request.getSession().printOutput(500, "Failed getting " + rslave.getName() +
						" location from freegeoip.net");
			}

			slaveLocations.put(rslave.getName(), slaveLocation);

			// Sort servers based on slave location
			DistanceFromMeComparator myComparator = new DistanceFromMeComparator(
					slaveLocation.getLatitude(), slaveLocation.getLongitude());
			TreeSet<SpeedTestServer> closestServers = new TreeSet<SpeedTestServer>(myComparator);
			closestServers.addAll(_servers);

			if (listservers || testServerID == 0) {
				int i = 0;
				while(!closestServers.isEmpty() && i < 5) {
					SpeedTestServer server = closestServers.pollFirst();
					if (listservers) {
						String distance = _numberFormat.format(
								SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(),
										slaveLocation.getLatitude(), slaveLocation.getLongitude(), 'K'));
						request.getSession().printOutput(rslave.getName() + " :: Server -> " +
								server.getSponsor() + " (" + server.getCountry() + ") [" +
								distance + " km] (" + server.getId() + ")");
					} else {
						testServers.put(server.getUrl(),server);
					}
					i++;
				}
				if (listservers) {
					break;
				}
			} else {
				for (SpeedTestServer server : closestServers) {
					if (server.getId() == testServerID) {
						testServers.put(server.getUrl(),server);
						break;
					}
				}
			}
			if (testServers.isEmpty()) {
				request.getSession().printOutput(500, "Something went wrong, could not get test server for " + rslave.getName());
				break;
			}

			usedServers.putAll(testServers);

			request.getSession().printOutput(200, "Starting test on " + rslave.getName());
			Callable<SpeedTestInfo> slaveThread = new SpeedTestCallable(rslave, testServers);
			Future<SpeedTestInfo> future = executor.submit(slaveThread);
			slaveThreadList.add(future);
		}

		for(Future<SpeedTestInfo> fut : slaveThreadList){
			try {
				// Wait for slave threads to exit and print result
				SpeedTestInfo result = fut.get();
				if (result != null && result.getURL() != null) {
					SpeedTestServer server = usedServers.get(result.getURL());
					SlaveLocation loc = slaveLocations.get(result.getSlaveName());
					String distance = _numberFormat.format(
							SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(),
									loc.getLatitude(), loc.getLongitude(), 'K'));
					request.getSession().printOutput(200, result.getSlaveName() + " :: Server -> " + server.getSponsor() + " (" + server.getCountry() + ") [" +
							distance + " km] (" + server.getId() + ") : " + result.getLatency()+" ms");
					request.getSession().printOutput(200, result.getSlaveName() + " :: Up -> " + _numberFormat.format(result.getUp()) +
							" Mbit/s  <>  Down -> " + _numberFormat.format(result.getDown())+ " Mbit/s");
				}
			} catch (InterruptedException e) {
				request.getSession().printOutput(500,e.getMessage());
			} catch (ExecutionException e) {
				request.getSession().printOutput(500,e.getMessage());
			}
		}
		//shut down the executor service now
		executor.shutdown();

		return null;
	}
}
