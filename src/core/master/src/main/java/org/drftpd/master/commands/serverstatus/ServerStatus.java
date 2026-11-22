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
package org.drftpd.master.commands.serverstatus;

import org.drftpd.common.dynamicdata.Key;
import org.drftpd.common.slave.SystemInfo;
import org.drftpd.common.util.Bytes;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.commands.*;
import org.drftpd.master.network.BaseFtpConnection;
import org.drftpd.master.network.Session;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.drftpd.master.slavemanagement.SystemInfoReply;
import org.drftpd.master.util.Time;
import org.drftpd.master.vfs.CommitManager;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.lang.management.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * @author fr0w
 * @version $Id$
 */
public class ServerStatus extends CommandInterface {
    protected static final Key<Long> CONNECTTIME = new Key<>(ServerStatus.class, "connecttime");

    /** Section names understood by doStatus; anything else after hardware/hw is a target name. */
    static final Set<String> SECTIONS = Set.of("os", "vm", "memory", "classes", "threads", "gc", "cm", "cpu",
            "network", "slaves", "hardware", "hw", "all");

    // Slaves are asked in parallel so that one slow slave does not add up its timeout to the others
    private static final ExecutorService HW_POOL = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ServerStatus-sysinfo");
        t.setDaemon(true);
        return t;
    });

    private ResourceBundle _bundle;

    public void initialize(String method, String pluginName, StandardCommandManager cManager) {
        super.initialize(method, pluginName, cManager);
        StatusSubscriber.checkSubscription();
        _bundle = cManager.getResourceBundle();
    }

    public CommandResponse doMasterUptime(CommandRequest request) {
        CommandResponse response = new CommandResponse(200);
        Map<String, Object> env = new HashMap<>();

        long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
        env.put("uptime", Time.formatTime(uptime));
        response.setMessage(request.getSession().jprintf(_bundle, "status.master.uptime", env, request.getUser()));

        return response;
    }

    public CommandResponse doSlaveUptime(CommandRequest request) throws ImproperUsageException {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        String slaveName = request.getArgument();
        Session session = request.getSession();

        try {
            RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
            response.addComment(makeOutput(session, rslave));
        } catch (ObjectNotFoundException e2) {
            env.put("slave", slaveName);
            response.addComment(session.jprintf(_bundle, env, "status.slave.notfound"));
        }
        return response;
    }

    public CommandResponse doSlavesUptime(CommandRequest request) {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        List<RemoteSlave> rslaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();

        for (RemoteSlave rslave : rslaves) {
            response.addComment(makeOutput(request.getSession(), rslave));
        }

        return response;
    }

    private String makeOutput(Session session, RemoteSlave rslave) {
        Map<String, Object> env = new HashMap<>();

        env.put("slave", rslave.getName());

        if (rslave.isAvailable()) {
            long connectTime = rslave.getTransientKeyedMap().getObjectLong(CONNECTTIME);
            long uptime = System.currentTimeMillis() - connectTime;
            env.put("uptime", Time.formatTime(uptime));
            return session.jprintf(_bundle, env, "status.slave.uptime");
        }
        return session.jprintf(_bundle, env, "status.slave.offline");
    }

    public CommandResponse doStatus(CommandRequest request) throws ImproperUsageException {
        CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
        Map<String, Object> env = new HashMap<>();
        Session session = request.getSession();

        if (!request.hasArgument()) {
            throw new ImproperUsageException();
        }

        // "hardware <name>" / "hw <name>": the token following hardware is a slave name (or "master") unless
        // it is a section name. It is taken out (keeping its case) before the sections are parsed.
        List<String> tokens = new ArrayList<>(Arrays.asList(request.getArgument().replaceAll(",", " ").trim().split("\\s+")));
        String hwTarget = removeHardwareTarget(tokens);

        String args = String.join(" ", tokens).toLowerCase();
        StringTokenizer st = new StringTokenizer(args);
        boolean isAll = args.contains("all");

        // avoid output repetition
        // ex: gc, vm, all

        while (st.hasMoreTokens()) {
            String arg = st.nextToken().trim();
            if (arg.equals("all")) {
                // avoid output repetition
                isAll = true;
            }

            if (arg.equals("os") || isAll) {
                OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
                env.put("os.name", omx.getName());
                env.put("os.version", omx.getVersion());
                env.put("os.arch", omx.getArch());
                response.addComment(session.jprintf(_bundle, env, "status.osinfo"));
            }

            if (arg.equals("vm") || isAll) {
                RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
                env.put("vm.name", rmx.getVmName());
                env.put("vm.version", System.getProperty("java.version"));
                env.put("vm.vendor", rmx.getVmVendor());
                response.addComment(session.jprintf(_bundle, env, "status.vminfo"));
            }

            if (arg.equals("memory") || isAll) {
                MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                env.put("heap.used", Bytes.formatBytes(heap.getUsed()));
                env.put("heap.available", Bytes.formatBytes(heap.getCommitted()));
                env.put("heap.max", Bytes.formatBytes(heap.getMax()));
                response.addComment(session.jprintf(_bundle, env, "status.heap"));

                MemoryUsage nonheap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
                env.put("nonheap.used", Bytes.formatBytes(nonheap.getUsed()));
                env.put("nonheap.available", Bytes.formatBytes(nonheap.getCommitted()));
                env.put("nonheap.max", Bytes.formatBytes(nonheap.getMax()));
                response.addComment(session.jprintf(_bundle, env, "status.nonheap"));
            }

            if (arg.equals("classes") || isAll) {
                ClassLoadingMXBean cmx = ManagementFactory.getClassLoadingMXBean();
                env.put("loaded.classes", cmx.getLoadedClassCount());
                env.put("unloaded.classes", cmx.getUnloadedClassCount());
                env.put("total.classes", cmx.getTotalLoadedClassCount());
                response.addComment(session.jprintf(_bundle, env, "status.classes"));
            }

            if (arg.equals("threads") || isAll) {
                ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
                env.put("current.threads", tmx.getThreadCount());
                env.put("max.threads", tmx.getPeakThreadCount());
                env.put("total.threads", tmx.getTotalStartedThreadCount());
                response.addComment(session.jprintf(_bundle, env, "status.threads"));
            }

            if (arg.equals("gc") || isAll) {
                long collectionCount = 0;
                long collectionTime = 0;
                for (GarbageCollectorMXBean gmx : ManagementFactory.getGarbageCollectorMXBeans()) {
                    collectionCount += gmx.getCollectionCount();
                    collectionTime += gmx.getCollectionTime();
                }
                env.put("collection.count", String.valueOf(collectionCount));

                if (collectionTime > 1000)
                    env.put("collection.time", Time.formatTime(collectionTime));
                else
                    env.put("collection.time", collectionTime + "ms");

                response.addComment(session.jprintf(_bundle, env, "status.gcinfo"));
            }

            if (arg.equals("cm") || isAll) {
                CommitManager cm = CommitManager.getCommitManager();
                env.put("cm.queue", cm.getQueueSize());
                env.put("cm.state", cm.getThreadState());

                response.addComment(session.jprintf(_bundle, env, "status.cminfo"));
            }

            if (arg.equals("cpu") || isAll) {
                OperatingSystemMXBean omx = ManagementFactory.getOperatingSystemMXBean();
                env.put("cpu.arch", omx.getArch());
                env.put("cpu.cores", omx.getAvailableProcessors());
                double loadAvg = omx.getSystemLoadAverage();
                env.put("cpu.load", loadAvg >= 0 ? String.format("%.2f", loadAvg) : "N/A");
                response.addComment(session.jprintf(_bundle, env, "status.cpuinfo"));
            }

            if (arg.equals("network") || isAll) {
                Properties props = GlobalContext.getConfig().getMainProperties();
                // The bind address is only shown on FTP: IRC output can go to a public channel
                if (session instanceof BaseFtpConnection) {
                    env.put("net.bindip", props.getProperty("master.ip", "0.0.0.0"));
                }
                env.put("net.port", props.getProperty("master.port", "?"));

                List<RemoteSlave> slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
                int online = 0;
                for (RemoteSlave rs : slaves) {
                    if (rs.isOnline())
                        online++;
                }
                env.put("net.slaves.online", online);
                env.put("net.slaves.total", slaves.size());
                response.addComment(session.jprintf(_bundle, env, "status.netinfo"));
            }

            if (arg.equals("hardware") || arg.equals("hw") || isAll) {
                if (hwTarget == null) {
                    addHardwareSummary(response, session);
                } else {
                    addHardwareDetail(response, session, hwTarget);
                }
            }

            if (arg.equals("slaves")) {
                // Slave IP addresses are only exposed to FTP sessions, never to IRC (output goes to channels)
                boolean showAddress = session instanceof BaseFtpConnection;
                List<RemoteSlave> slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
                for (RemoteSlave rs : slaves) {
                    Map<String, Object> slaveEnv = new HashMap<>();
                    slaveEnv.put("slave.name", rs.getName());
                    boolean online = rs.isOnline();
                    slaveEnv.put("slave.online", online ? "Online" : "Offline");
                    if (online) {
                        // Address and TLS details are only valid while the slave is connected
                        if (showAddress) {
                            slaveEnv.put("slave.address", rs.getRemoteAddress());
                        }
                        slaveEnv.put("slave.tls.protocol", rs.getTlsProtocol());
                        slaveEnv.put("slave.tls.cipher", rs.getTlsCipherSuite());
                    }
                    response.addComment(session.jprintf(_bundle, slaveEnv,
                            online ? "status.slaveinfo" : "status.slaveinfo.offline"));
                }
            }

            if (isAll) {
                // no need to output repeated
                break;
            }
        }

        return response;
    }

    /**
     * Finds the first "hardware"/"hw" token followed by a token that is not a section name, removes that
     * following token from the list and returns it (with its original case).
     *
     * @return the target name (a slave name or "master"), or null if there is none
     */
    static String removeHardwareTarget(List<String> tokens) {
        for (int i = 0; i < tokens.size() - 1; i++) {
            String token = tokens.get(i).toLowerCase();
            if ((token.equals("hardware") || token.equals("hw")) && !SECTIONS.contains(tokens.get(i + 1).toLowerCase())) {
                return tokens.remove(i + 1);
            }
        }
        return null;
    }

    private static SystemInfo collectMasterInfo() {
        return SystemInfo.collect(GlobalContext.VERSION, List.of(HardwareStatus.MASTER_ROOT));
    }

    private void addHardwareSummary(CommandResponse response, Session session) {
        List<RemoteSlave> slaves = GlobalContext.getGlobalContext().getSlaveManager().getSlaves();
        List<Future<SystemInfoReply>> replies = new ArrayList<>();
        for (RemoteSlave rs : slaves) {
            replies.add(HW_POOL.submit(() -> rs.fetchSystemInfo(HardwareStatus.SLAVE_TIMEOUT_MILLIS)));
        }

        response.addComment(session.jprintf(_bundle,
                HardwareStatus.summaryEnv(HardwareStatus.MASTER, collectMasterInfo()), "status.hw.summary"));

        for (int i = 0; i < slaves.size(); i++) {
            String name = slaves.get(i).getName();
            SystemInfoReply reply = await(replies.get(i));
            if (reply.getStatus() == SystemInfoReply.Status.OK) {
                response.addComment(session.jprintf(_bundle,
                        HardwareStatus.summaryEnv(name, reply.getSystemInfo()), "status.hw.summary"));
            } else {
                response.addComment(session.jprintf(_bundle, failureEnv(name),
                        HardwareStatus.failureKey(reply.getStatus())));
            }
        }
    }

    private void addHardwareDetail(CommandResponse response, Session session, String target) {
        // Root paths are only exposed to FTP sessions, IRC output can go to a public channel
        boolean showPaths = session instanceof BaseFtpConnection;
        String name;
        SystemInfo si;
        if (target.equalsIgnoreCase(HardwareStatus.MASTER)) {
            name = HardwareStatus.MASTER;
            si = collectMasterInfo();
        } else {
            RemoteSlave rs;
            try {
                rs = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(target);
            } catch (ObjectNotFoundException e) {
                response.addComment(session.jprintf(_bundle, failureEnv(target), "status.hw.notfound"));
                return;
            }
            name = rs.getName();
            SystemInfoReply reply = rs.fetchSystemInfo(HardwareStatus.SLAVE_TIMEOUT_MILLIS);
            if (reply.getStatus() != SystemInfoReply.Status.OK) {
                response.addComment(session.jprintf(_bundle, failureEnv(name),
                        HardwareStatus.failureKey(reply.getStatus())));
                return;
            }
            si = reply.getSystemInfo();
        }

        Map<String, Object> env = HardwareStatus.detailEnv(name, si);
        for (String key : new String[]{"status.hw.detail.header", "status.hw.detail.os", "status.hw.detail.cpu",
                "status.hw.detail.memory", "status.hw.detail.jvm", "status.hw.detail.disks"}) {
            response.addComment(session.jprintf(_bundle, env, key));
        }
        for (Map<String, Object> rootEnv : HardwareStatus.rootEnvs(name, si, showPaths)) {
            response.addComment(session.jprintf(_bundle, rootEnv, "status.hw.detail.root"));
        }
    }

    private static Map<String, Object> failureEnv(String name) {
        Map<String, Object> env = new HashMap<>();
        env.put("hw.name", name);
        return env;
    }

    private static SystemInfoReply await(Future<SystemInfoReply> future) {
        try {
            // fetchSystemInfo has its own timeout, this one is only a safety net
            return future.get(HardwareStatus.SLAVE_TIMEOUT_MILLIS + 5000L, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, null);
        } catch (TimeoutException e) {
            return SystemInfoReply.of(SystemInfoReply.Status.TIMEOUT, null);
        } catch (ExecutionException e) {
            return SystemInfoReply.of(SystemInfoReply.Status.ERROR, null);
        }
    }
}
