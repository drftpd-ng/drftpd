package org.drftpd.commands.serverstatus;

import java.lang.management.ManagementFactory;
import java.util.List;

import org.drftpd.GlobalContext;
import org.drftpd.Time;
import org.drftpd.commandmanager.CommandInterface;
import org.drftpd.commandmanager.CommandRequest;
import org.drftpd.commandmanager.CommandResponse;
import org.drftpd.commandmanager.StandardCommandManager;
import org.drftpd.dynamicdata.Key;
import org.drftpd.exceptions.ObjectNotFoundException;
import org.drftpd.master.RemoteSlave;
import org.drftpd.master.Session;
import org.drftpd.util.ExtendedPropertyResourceBundle;
import org.drftpd.util.ReplacerUtils;
import org.tanesha.replacer.ReplacerEnvironment;

public class ServerStatus extends CommandInterface {
	protected static Key CONNECTTIME = new Key(ServerStatus.class, "connectime", Long.class);
	
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
	
	public CommandResponse doSlaveUptime(CommandRequest request) {
		CommandResponse response = StandardCommandManager.genericResponse("RESPONSE_200_COMMAND_OK");
	    ReplacerEnvironment env = new ReplacerEnvironment();
	    
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
} 

