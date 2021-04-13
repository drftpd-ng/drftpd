/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * DrFTPD is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with DrFTPD; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.drftpd.speedtestnet.master;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.bushe.swing.event.annotation.AnnotationProcessor;
import org.bushe.swing.event.annotation.EventSubscriber;
import org.drftpd.common.util.ConfigLoader;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.event.ReloadEvent;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.slave.exceptions.ObjectNotFoundException;
import org.drftpd.speedtestnet.common.SpeedTestInfo;
import org.drftpd.speedtestnet.master.protocol.SpeedTestCallable;

import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author scitz0
 */
public class SpeedTest extends CommandInterface {

    private static final Logger logger = LogManager.getLogger(SpeedTestUtils.class);
    private static final DecimalFormat _numberFormat = new DecimalFormat("#.00");

    private ResourceBundle _bundle;
    private HashSet<SpeedTestServer> _servers;
    private char _unit;
    private String _unitSuffix;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        // Subscribe to events
        AnnotationProcessor.process(this);
        _bundle = cManager.getResourceBundle();

        _servers = null;
        readConfig();
    }

    /**
     * Reads 'config/plugins/speedtest.net.conf'
     */
    private void readConfig() {
        Properties props = ConfigLoader.loadPluginConfig("speedtest.net.conf");
        String unit = props.getProperty("distance.unit", "K");
        _unit = unit.length() == 0 ? 'K' : unit.charAt(0);
        if (_unit != 'M' && _unit != 'N') {
            _unit = 'K';
        }
        switch (_unit) {
            case 'K':
                _unitSuffix = "km";
                break;
            case 'M':
                _unitSuffix = "mi";
                break;
            case 'N':
                _unitSuffix = "nmi";
                break;
        }
    }

    private void refreshServers() {
        _servers = SpeedTestUtils.getClosetsServers();
    }

    public CommandResponse doREFRESHSPEEDTEST(CommandRequest request) {
        refreshServers();
        return new CommandResponse(200, request.getSession().jprintf(_bundle, "speedtest.servers.refresh", null, request.getUser()));
    }


    public CommandResponse doSPEEDTEST(CommandRequest request) throws ImproperUsageException {
        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        Map<String, Object> env = new HashMap<>();
        StringTokenizer st = new StringTokenizer(request.getArgument());

        // First run?, load server list
        if (_servers == null) {
            refreshServers();
            request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "speedtest.servers.refresh", env, request.getUser()));
        }

        if (st.countTokens() == 0) {
            throw new ImproperUsageException();
        }
        String slaveName = st.nextToken();

        boolean allSlaves = false;
        boolean wildcardSlaves = false;
        if (slaveName.equals("*")) {
            allSlaves = true;
        } else if (slaveName.contains("*")) {
            wildcardSlaves = true;
        }

        // Handle optional options if present
        int testServerID = 0;
        boolean listservers = false;
        boolean updateLocation = false;
        if (st.hasMoreTokens()) {
            String option = st.nextToken();
            if (option.equals("-list")) {
                listservers = true;
            } else if (option.equals("-update")) {
                updateLocation = true;
            } else if (option.matches("\\d+")) {
                try {
                    testServerID = Integer.parseInt(option);
                } catch(NumberFormatException e) {
                    throw new ImproperUsageException(e.getMessage());
                }
            } else {
                throw new ImproperUsageException();
            }
        }

        if (_servers == null) {
            return new CommandResponse(500, request.getSession().jprintf(_bundle, "speedtest.servers.null", env, request.getUser()));
        } else if (_servers.isEmpty()) {
            return new CommandResponse(500, request.getSession().jprintf(_bundle, "speedtest.servers.empty", env, request.getUser()));
        }

        ArrayList<RemoteSlave> rslaves = new ArrayList<>();
        try {
            if (allSlaves) {
                rslaves.addAll(GlobalContext.getGlobalContext().getSlaveManager().getSlaves());
            } else if (wildcardSlaves) {
                slaveName = slaveName.substring(0, slaveName.length() - 1); // Remove wildcard
                for (RemoteSlave slave : GlobalContext.getGlobalContext().getSlaveManager().getSlaves()) {
                    if (slave.getName().startsWith(slaveName)) {
                        rslaves.add(slave);
                    }
                }
            } else {
                rslaves.add(GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName));
            }
        } catch (ObjectNotFoundException e) {
            env.put("slave.name", slaveName);
            return new CommandResponse(500, request.getSession().jprintf(_bundle, "speedtest.slavename.error", env, request.getUser()));
        }

        HashMap<String, SpeedTestServer> usedServers = new HashMap<>();
        HashMap<String, SlaveLocation> slaveLocations = new HashMap<>();

        ExecutorService executor = Executors.newFixedThreadPool(rslaves.size());
        List<Future<SpeedTestInfo>> slaveThreadList = new ArrayList<>();

        for (RemoteSlave rslave : rslaves) {
            env.put("slave.name", rslave.getName());
            if (!rslave.isOnline()) {
                request.getSession().printOutput(500, request.getSession().jprintf(_bundle, "speedtest.slave.offline", env, request.getUser()));
                continue;
            }
            HashMap<String, SpeedTestServer> testServers = new HashMap<>();

            SlaveLocation slaveLocation = new SlaveLocation();
            String lat = rslave.getProperty("lat");
            String lon = rslave.getProperty("lon");
            if (NumberUtils.isParsable(lat)) {
                slaveLocation.setLatitude(Double.parseDouble(lat));
            }
            if (NumberUtils.isParsable(lon)) {
                slaveLocation.setLongitude(Double.parseDouble(lon));
            }
            if (updateLocation || (slaveLocation.getLatitude() == 0 && slaveLocation.getLongitude() == 0)) {
                // Location not set yet, let's find it
                slaveLocation = SpeedTestUtils.getSlaveLocation(rslave);
            }
            if (slaveLocation.getLatitude() == 0 && slaveLocation.getLongitude() == 0) {
                logger.warn("Slave location not found [{}]", slaveLocation);
                request.getSession().printOutput(500, request.getSession().jprintf(_bundle, "speedtest.slave.geoip.error", env, request.getUser()));
                continue;
            }
            logger.debug("Found location for slave {} -> [{}]", rslave.getName(), slaveLocation);
            // Persist the lat/lon
            rslave.setProperty("lat", String.valueOf(slaveLocation.getLatitude()));
            rslave.setProperty("lon", String.valueOf(slaveLocation.getLongitude()));

            slaveLocations.put(rslave.getName(), slaveLocation);

            // Sort servers based on slave location
            DistanceFromMeComparator myComparator = new DistanceFromMeComparator(slaveLocation.getLatitude(), slaveLocation.getLongitude());
            TreeSet<SpeedTestServer> closestServers = new TreeSet<>(myComparator);
            closestServers.addAll(_servers);

            if (listservers || testServerID == 0) {
                int i = 0;
                while (!closestServers.isEmpty() && i < 5) {
                    SpeedTestServer server = closestServers.pollFirst();
                    if (listservers) {
                        SpeedTestUtils.addServerEnvVariables(server, env);
                        double distance = SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(), slaveLocation.getLatitude(), slaveLocation.getLongitude(), _unit);
                        env.put("distance", distance < 1.0 ? "<1.00" : _numberFormat.format(distance));
                        env.put("unit", _unitSuffix);
                        request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "speedtest.slave.server.list", env, request.getUser()));
                    } else {
                        testServers.put(server.getUrl(), server);
                    }
                    i++;
                }
                if (listservers) {
                    continue;
                }
            } else {
                for (SpeedTestServer server : closestServers) {
                    if (server.getId() == testServerID) {
                        testServers.put(server.getUrl(), server);
                        break;
                    }
                }
            }
            if (testServers.isEmpty()) {
                // Server list not empty but could not find any test server, id must not be valid
                env.put("server.id", testServerID);
                request.getSession().printOutput(500, request.getSession().jprintf(_bundle, "speedtest.server.id.error", env, request.getUser()));
                continue;
            }

            usedServers.putAll(testServers);

            request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "speedtest.start.test", env, request.getUser()));
            Callable<SpeedTestInfo> slaveThread = new SpeedTestCallable(rslave, testServers);
            Future<SpeedTestInfo> future = executor.submit(slaveThread);
            slaveThreadList.add(future);
        }

        for (Future<SpeedTestInfo> fut : slaveThreadList) {
            try {
                // Wait for slave threads to exit and print result
                SpeedTestInfo result = fut.get();
                if (result != null && result.getURL() != null) {
                    SpeedTestServer server = usedServers.get(result.getURL());
                    server.setLatency(result.getLatency());
                    SpeedTestUtils.addServerEnvVariables(server, env);
                    SlaveLocation loc = slaveLocations.get(result.getSlaveName());
                    env.put("slave.name", result.getSlaveName());
                    env.put("slave.lat", loc.getLatitude());
                    env.put("slave.lon", loc.getLongitude());
                    double distance = SpeedTestUtils.getDistance(server.getLatitude(), server.getLongitude(), loc.getLatitude(), loc.getLongitude(), _unit);
                    env.put("distance", distance < 1.0 ? "<1.00" : _numberFormat.format(distance));
                    env.put("unit", _unitSuffix);
                    env.put("speed.up", _numberFormat.format(result.getUp()));
                    env.put("speed.down", _numberFormat.format(result.getDown()));
                    request.getSession().printOutput(200, request.getSession().jprintf(_bundle, "speedtest.slave.result", env, request.getUser()));
                }
            } catch (InterruptedException | ExecutionException e) {
                request.getSession().printOutput(500, e.getMessage());
            }
        }
        //shut down the executor service now
        executor.shutdown();

        return StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
    }

    @EventSubscriber
    public void onReloadEvent(ReloadEvent event) {
        logger.info("Received reload event, reloading");
        readConfig();
    }

}
