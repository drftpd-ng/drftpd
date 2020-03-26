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
package org.drftpd.commands.serverstatus;

import org.drftpd.commands.*;
import org.drftpd.master.GlobalContext;
import org.drftpd.master.Time;
import org.drftpd.master.common.Bytes;
import org.drftpd.master.common.dynamicdata.Key;
import org.drftpd.master.master.RemoteSlave;
import org.drftpd.master.master.Session;
import org.drftpd.slave.exceptions.ObjectNotFoundException;

import java.lang.management.*;
import java.util.*;

/**
 * @author fr0w
 * @version $Id$
 */
public class ServerStatus extends CommandInterface {
	protected static final Key<Long> CONNECTTIME = new Key<>(ServerStatus.class, "connecttime");
	
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
		
		String args = request.getArgument().replaceAll(",", " ");
		StringTokenizer st = new StringTokenizer(args);
		boolean isAll = false;
		
		if (args.contains("all")) {
			// avoid output repetition
			// ex: gc, vm, all
			isAll = true;
		}
		
		while (st.hasMoreTokens()) {
			String arg = st.nextToken().toLowerCase().trim();
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
					env.put("collection.time", collectionTime+"ms");

				response.addComment(session.jprintf(_bundle, env, "status.gcinfo"));
			}
			
			if (isAll) {
				// no need to output repeated 
				break;
			}
		}
		
		return response;
	}
} 

