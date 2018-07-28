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

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.*;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.util.ExtendedPropertyResourceBundle;
import org.tanesha.replacer.ReplacerEnvironment;

import java.lang.management.*;
import java.util.List;
import java.util.StringTokenizer;

/**
 * @author fr0w
 * @version $Id$
 */
public class ServerStatus extends CommandInterface {
	protected static final Key<Long> CONNECTTIME = new Key<>(ServerStatus.class, "connecttime");
	
	private ExtendedPropertyResourceBundle _bundle;
	private String _keyPrefix;
	
	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		StatusSubscriber.checkSubscription();
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}
	
	public CommandResponse doMasterUptime(CommandRequest request) {
		Session session = request.getSession();
		CommandResponse response = new CommandResponse(200);	
		ReplacerEnvironment env = new ReplacerEnvironment();
		
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		env.add("uptime", Time.formatTime(uptime));		
		response.setMessage(session.jprintf(_bundle, env, _keyPrefix+"master.uptime"));
		
		return response;		
	}
	
	public CommandResponse doSlaveUptime(CommandRequest request) throws ImproperUsageException {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	    ReplacerEnvironment env = new ReplacerEnvironment();
	    
		if (!request.hasArgument()) {
			throw new ImproperUsageException();
		}
	    
	    String slaveName = request.getArgument();
	    Session session = request.getSession();
	    
	    try {
            RemoteSlave rslave = GlobalContext.getGlobalContext().getSlaveManager().getRemoteSlave(slaveName);
            response.addComment(makeOutput(session, rslave));            
	    } catch (ObjectNotFoundException e2) {
            env.add("slave", slaveName);
            response.addComment(session.jprintf(_bundle, env, _keyPrefix+"slave.notfound"));
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
		ReplacerEnvironment env = new ReplacerEnvironment();
		
        env.add("slave", rslave.getName()); 
        
        if (rslave.isAvailable()) {
        	long connectTime = rslave.getTransientKeyedMap().getObjectLong(CONNECTTIME);
        	long uptime = System.currentTimeMillis() - connectTime;
        	env.add("uptime", Time.formatTime(uptime));	
    		return session.jprintf(_bundle, env, _keyPrefix+"slave.uptime");
        }
		return session.jprintf(_bundle, env, _keyPrefix+"slave.offline");
	}
	
	public CommandResponse doStatus(CommandRequest request) throws ImproperUsageException {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
		ReplacerEnvironment env = new ReplacerEnvironment();
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
				env.add("os.name", omx.getName());
				env.add("os.version", omx.getVersion());
				env.add("os.arch", omx.getArch());
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.osinfo"));
			}

			if (arg.equals("vm") || isAll) {
				RuntimeMXBean rmx = ManagementFactory.getRuntimeMXBean();
				env.add("vm.name", rmx.getVmName());
				env.add("vm.version", System.getProperty("java.version"));
				env.add("vm.vendor", rmx.getVmVendor());
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.vminfo"));
			}

			if (arg.equals("memory") || isAll) { 
				MemoryUsage heap = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
				env.add("heap.used", Bytes.formatBytes(heap.getUsed()));
				env.add("heap.available", Bytes.formatBytes(heap.getCommitted()));
				env.add("heap.max", Bytes.formatBytes(heap.getMax()));
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.heap"));

				MemoryUsage nonheap = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
				env.add("nonheap.used", Bytes.formatBytes(nonheap.getUsed()));
				env.add("nonheap.available", Bytes.formatBytes(nonheap.getCommitted()));
				env.add("nonheap.max", Bytes.formatBytes(nonheap.getMax()));
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.nonheap"));
			}
			
			if (arg.equals("classes") || isAll) {
				ClassLoadingMXBean cmx = ManagementFactory.getClassLoadingMXBean();
				env.add("loaded.classes", cmx.getLoadedClassCount());
				env.add("unloaded.classes", cmx.getUnloadedClassCount());
				env.add("total.classes", cmx.getTotalLoadedClassCount());
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.classes"));
			}
			
			if (arg.equals("threads") || isAll) {
				ThreadMXBean tmx = ManagementFactory.getThreadMXBean();
				env.add("current.threads", tmx.getThreadCount());
				env.add("max.threads", tmx.getPeakThreadCount());
				env.add("total.threads", tmx.getTotalStartedThreadCount());
				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.threads"));
			}

			if (arg.equals("gc") || isAll) {
				long collectionCount = 0;
				long collectionTime = 0;
				for (GarbageCollectorMXBean gmx : ManagementFactory.getGarbageCollectorMXBeans()) {
					collectionCount += gmx.getCollectionCount();
					collectionTime += gmx.getCollectionTime();
				}
				env.add("collection.count", String.valueOf(collectionCount));
				
				if (collectionTime > 1000)
					env.add("collection.time", Time.formatTime(collectionTime));
				else
					env.add("collection.time", collectionTime+"ms");

				response.addComment(session.jprintf(_bundle, env, _keyPrefix+"status.gcinfo"));
			}
			
			if (isAll) {
				// no need to output repeated 
				break;
			}
		}
		
		return response;
	}
} 

