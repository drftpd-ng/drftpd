package org.drftpd.commands.serverstatus;

import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;
import java.util.StringTokenizer;

import org.drftpd.Bytes;
import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.ImproperUsageException;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.util.ExtendedPropertyResourceBundle;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

public class ServerStatus extends CommandInterface {
	protected static final Key<Long> CONNECTTIME = new Key<Long>(ServerStatus.class, "connecttime");
	
	private ExtendedPropertyResourceBundle _bundle;
	private String _keyPrefix;
	
	public void initialize(String method, String pluginName, StandardCommandManager cManager) {
		super.initialize(method, pluginName, cManager);
		StatusSubscriber.checkSubscription();
		_bundle = cManager.getResourceBundle();
		_keyPrefix = this.getClass().getName()+".";
	}
	
	public CommandResponse doMasterUptime(CommandRequest request) {
		CommandResponse response = new CommandResponse(200);	
		ReplacerEnvironment env = new ReplacerEnvironment();
		
		long uptime = ManagementFactory.getRuntimeMXBean().getUptime();
		env.add("uptime", Time.formatTime(uptime));		
		response.setMessage(ReplacerUtils.jprintf(_keyPrefix+"master.uptime", env, _bundle));
		
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
        } else {
        	return session.jprintf(_bundle, env, _keyPrefix+"slave.offline");
        }
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
		
		if (args.indexOf("all") != -1) {
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
				env.add("vm.version", rmx.getVmVersion());
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

