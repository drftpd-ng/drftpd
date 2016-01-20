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
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.GlobalContext;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.event.ReloadEvent;
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
import java.util.Properties;
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
	private char _unit;
	private String _unitSuffix;
	private static final DecimalFormat _numberFormat = new DecimalFormat("#.00");

	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		// Subscribe to events
		AnnotationProcessor.process(this);
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
		_servers = SpeedTestUtils.getClosetsServers();
		readConfig();
	}

	/**
	 * Reads 'conf/plugins/speedtest.net.conf'
	 */
	private void readConfig() {
		Properties props = GlobalContext.getGlobalContext().getPluginsConfig().getPropertiesForPlugin("speedtest.net");

		String unit = props.getProperty("distance.unit", "K");
		_unit = unit.length() == 0 ? 'K' : unit.charAt(0);
		if (_unit != 'M' && _unit != 'N') {
			_unit = 'K';
		}
		switch (_unit) {
			case 'K': _unitSuffix = "km";
				break;
			case 'M': _unitSuffix = "mi";
				break;
			case 'N': _unitSuffix = "nmi";
				break;
		}
	}

	public CommandResponse doSITE_SPEEDTEST(CommandRequest request) throws ImproperUsageException {
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}

		String[] args = request.getArgument().split(" ");

		int testServerID = 0;
		String slaveName = args[0];
		boolean allSlaves = slaveName.equals("*");
		boolean wildcardSlaves = slaveName.endsWith("*") && !allSlaves;
		boolean listservers = false;

		ReplacerEnvironment env = new ReplacerEnvironment(SiteBot.GLOBAL_ENV);

		if (args[0].equals("-refresh")) {
			_servers = SpeedTestUtils.getClosetsServers();
			return new CommandResponse(200, request.getSession().jprintf(
					_bundle, _keyPrefix+"servers.refresh", env, request.getUser()));
		}
		if (args.length == 2 && !allSlaves && !wildcardSlaves && args[1].equals("-list")) {
			listservers = true;
		} else if (args.length == 2 && args[1].matches("\\d+")) {
			testServerID = Integer.parseInt(args[1]);
		} else if (args.length != 1) {
			throw new ImproperUsageException();
		}

		if (_servers == null) {
			return new CommandResponse(500, request.getSession().jprintf(
					_bundle, _keyPrefix+"servers.null", env, request.getUser()));
		} else if (_servers.isEmpty()) {
			return new CommandResponse(500, request.getSession().jprintf(
					_bundle, _keyPrefix+"servers.empty", env, request.getUser()));
		}

		ArrayList<RemoteSlave> rslaves = new ArrayList<RemoteSlave>();
		try {
			if (allSlaves) {
				rslaves.addAll(GlobalContext.getGlobalContext().getSlaveManager().getSlaves());
			} else if (wildcardSlaves) {
				slaveName = slaveName.substring(0,slaveName.length()-1); // Remove wildcard
				for (RemoteSlave slave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
					if (slave.getName().startsWith(slaveName)) {
						rslaves.add(slave);
					}
				}
			} else {
				rslaves.add(GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName));
			}
		} catch (ObjectNotFoundException e) {
			env.add("slave.name", slaveName);
			return new CommandResponse(500, request.getSession().jprintf(
					_bundle, _keyPrefix+"slavename.error", env, request.getUser()));
		}

		HashMap<String, SpeedTestServer> usedServers = new HashMap<String, SpeedTestServer>();
		HashMap<String, SlaveLocation> slaveLocations = new HashMap<String, SlaveLocation>();

		ExecutorService executor = Executors.newFixedThreadPool(rslaves.size());
		List<Future<SpeedTestInfo>> slaveThreadList = new ArrayList<Future<SpeedTestInfo>>();

		for (RemoteSlave rslave : rslaves) {
			env.add("slave.name", rslave.getName());
			if (!rslave.isOnline()) {
				request.getSession().printOutput(500, request.getSession().jprintf(
						_bundle, _keyPrefix+"slave.offline", env, request.getUser()));
				continue;
			}
			HashMap<String, SpeedTestServer> testServers = new HashMap<String, SpeedTestServer>();

			SlaveLocation slaveLocation = SpeedTestUtils.getSlaveLocation(rslave);
			if (slaveLocation.getLatitude() == 0 || slaveLocation.getLongitude() == 0) {
				request.getSession().printOutput(500, request.getSession().jprintf(
						_bundle, _keyPrefix+"slave.geoip.error", env, request.getUser()));
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
						SpeedTestUtils.addServerEnvVariables(server, env);
						env.add("distance", _numberFormat.format(
								SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(),
										slaveLocation.getLatitude(), slaveLocation.getLongitude(), _unit)));
						env.add("unit", _unitSuffix);
						request.getSession().printOutput(200, request.getSession().jprintf(
								_bundle, _keyPrefix+"slave.server.list", env, request.getUser()));
					} else {
						testServers.put(server.getUrl(),server);
					}
					i++;
				}
				if (listservers) {
					continue;
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
				// Server list not empty but could not find any test server, id must not be valid
				env.add("server.id", testServerID);
				request.getSession().printOutput(500, request.getSession().jprintf(
						_bundle, _keyPrefix+"server.id.error", env, request.getUser()));
				continue;
			}

			usedServers.putAll(testServers);

			request.getSession().printOutput(200, request.getSession().jprintf(
					_bundle, _keyPrefix+"start.test", env, request.getUser()));
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
					server.setLatency(result.getLatency());
					SpeedTestUtils.addServerEnvVariables(server, env);
					SlaveLocation loc = slaveLocations.get(result.getSlaveName());
					env.add("slave.name", result.getSlaveName());
					env.add("slave.lat", loc.getLatitude());
					env.add("slave.lon", loc.getLongitude());
					env.add("distance", _numberFormat.format(
							SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(),
									loc.getLatitude(), loc.getLongitude(), _unit)));
					env.add("unit", _unitSuffix);
					env.add("speed.up", _numberFormat.format(result.getUp()));
					env.add("speed.down", _numberFormat.format(result.getDown()));
					request.getSession().printOutput(200, request.getSession().jprintf(
							_bundle, _keyPrefix+"slave.result", env, request.getUser()));
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

	@EventSubscriber
	public void onReloadEvent(ReloadEvent event) {
		readConfig();
	}
}
