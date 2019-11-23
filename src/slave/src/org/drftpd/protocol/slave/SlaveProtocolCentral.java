/*
 * This file is part of DrFTPD, Distributed FTP Daemon.
 *
 * DrFTPD is free software; you can redistribute it and/or modify it under the
 * terms of the GNU General Public License as published by the Free Software
 * Foundation; either version 2 of the License, or (at your option) any later
 * version.
 *
 * DrFTPD is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * DrFTPD; if not, write to the Free Software Foundation, Inc., 59 Temple Place,
 * Suite 330, Boston, MA 02111-1307 USA
 */
package org.drftpd.protocol.slave;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.protocol.HandshakeWrapper;
import org.drftpd.protocol.ProtocolException;
import org.drftpd.slave.Slave;
import org.drftpd.slave.async.AsyncCommandArgument;
import org.drftpd.slave.async.AsyncResponse;
import org.drftpd.slave.async.AsyncResponseException;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;

/**
 * SlaveProtocolCentral handles the load of all connected Handlers.<br>
 * These handlers represent a pluggable way of implementing different kind of
 * operations between Master and Slave. 
 * @author fr0w
 * @version $Id$
 */
public class SlaveProtocolCentral {
	public Map<String, HandlerWrapper> _handlersMap;
	public List<String> _protocols; 
	
	private static final Logger logger = LogManager.getLogger(SlaveProtocolCentral.class);
	
	private static final Class<?>[] CONSTRUCTORPARMS = { SlaveProtocolCentral.class };
	private static final Class<?>[] METHODPARMS = { AsyncCommandArgument.class };
	
	private Slave _slave = null;
	
	/**
	 * Instantiate the Central and load all connected handlers.
	 * @param slave
	 */
	public SlaveProtocolCentral(Slave slave) {
		_slave = slave;
		loadHandlers();
	}
	
	/**
	 * Whenever the Slave connects to the master, it receives a List containing all ProtocolExtensions loaded by master.<br>
	 * Slave will iterate through this List, checking if the requested extension is also loaded by the slave.<br>
	 * After the checking is done, Slave writes a {@link HandlerWrapper} to the socket and let master handles the rest.
	 * @see MasterProtocolCentral
	 * @see HandshakeWrapper
	 */
	@SuppressWarnings("unchecked")
	public void handshakeWithMaster() {
		HandshakeWrapper hw = new HandshakeWrapper();
		hw.setPluginStatus(true);
		
		try {
			// reading the plugin list from the socket
			Object o = getSlaveObject().getInputStream().readObject();
			
			if (o instanceof AsyncCommandArgument) {
				AsyncCommandArgument ac = (AsyncCommandArgument) o;
				throw new RuntimeException("An error happened: "+ ac.getArgs());
			}
			
			List<String> protocols = (List<String>) o;
			for (String protocol : protocols) {
                logger.debug("Checking availability for: {}", protocol);
				
				if (!_protocols.contains(protocol)) {
                    logger.error("{} not found, error!", protocol);
					hw.setPluginStatus(false);
					hw.setException(new ProtocolException(protocol + " was not found."));
					break;
				}

                logger.debug("{} is available.", protocol);
			}			
		} catch (Exception e) {
			logger.error("Exception during protocol handshake", e);
			hw.setException(new ProtocolException(e));
			hw.setPluginStatus(false);
		}
		
		try {
			getSlaveObject().getOutputStream().writeObject(hw);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/**
	 * Loads all connected Handlers and make them avaialable for later usage.
	 */
	private void loadHandlers() {
		HashMap<String, HandlerWrapper> handlers = new HashMap<>();
		ArrayList<String> protocols = new ArrayList<>();

		try {
			List<PluginObjectContainer<AbstractHandler>> loadedHandlers =
				CommonPluginUtils.getPluginObjectsInContainer(this, "slave", "Handler", "Class", "Method",
						CONSTRUCTORPARMS, new Object[] { this }, METHODPARMS);
			for (PluginObjectContainer<AbstractHandler> container : loadedHandlers) {
				String protocolName = 
					container.getPluginExtension().getDeclaringPluginDescriptor().getAttribute("ProtocolName").getValue();
				String name = container.getPluginExtension().getParameter("Name").valueAsString();
				if (!protocols.contains(protocolName)) {
					protocols.add(protocolName);
				}
				handlers.put(name, new HandlerWrapper(container.getPluginObject(), container.getPluginMethod()));
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for slave extension point 'Handler', possibly the slave"
					+" extension point definition has changed in the plugin.xml",e);
		}
		
		_handlersMap = Collections.unmodifiableMap(handlers);
		_protocols = Collections.unmodifiableList(protocols);
		
		for (String s : _protocols) {
            logger.debug("Loaded protocol extension: {}", s);
		}
		
		dumpHandlers();
	}
	
	private void dumpHandlers() {
		for (Entry<String, HandlerWrapper> entry : _handlersMap.entrySet()) {
			HandlerWrapper hw = entry.getValue();
            logger.debug("Handler for: {} -> {}.{}", entry.getKey(), hw.getAsyncHandler().getClass().getCanonicalName(), hw.getMethod().getName());
		}
	}
	
	public Slave getSlaveObject() {
		return _slave;
	}
	
	public AsyncResponse handleCommand(AsyncCommandArgument ac) {
		HandlerWrapper wrapper = _handlersMap.get(ac.getName());

		if (wrapper == null) {
			return new AsyncResponseException(ac.getIndex(), new Exception(ac.getName()+ " - Operation Not Supported"));
		}
		
		Method m = wrapper.getMethod();
		AbstractHandler ah = wrapper.getAsyncHandler();
		AsyncResponse ar = null;
		
		try {
			ar = (AsyncResponse) m.invoke(ah, new Object[]{ ac });
		} catch (Exception e) {
            logger.error("Unable to invoke: {}", m.toGenericString(), e);
            logger.error("Invokation failed due to: {}", m.toGenericString(), e.getCause());
			return new AsyncResponseException(ac.getIndex(), new Exception("Unable to invoke the proper handler", e));
		}
		
		return ar;
	}
}
