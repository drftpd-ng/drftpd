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
package org.drftpd.protocol.master;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.master.RemoteSlave;
import org.drftpd.protocol.HandshakeWrapper;
import org.drftpd.protocol.ProtocolException;
import org.drftpd.util.CommonPluginUtils;
import org.drftpd.util.PluginObjectContainer;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.*;
import java.util.Map.Entry;

/**
 * MasterProtocolCentral handles the load of all connected Protocol Extensions,
 * these extensions represent a pluggable way of implementing different kind of
 * operations between Master and Slave. 
 * @author fr0w
 * @version $Id$
 */
public class MasterProtocolCentral {
	private static final Logger logger = LogManager.getLogger(MasterProtocolCentral.class);	
	
	private Map<Class<?>, AbstractIssuer> _issuersMap;
	private List<String> _protocols;

	/**
	 * Unique constructor for MasterProtocolCentral object.
	 * By calling this constructor you will also start loading extensions.
	 */
	public MasterProtocolCentral()  {
		loadProtocolExtensions();
	}
	
	/**
	 * Iterate through all connected extensions, loading them.
	 */
	private void loadProtocolExtensions() {
		HashMap<Class<?>, AbstractIssuer> issuersMap =
                new HashMap<>();
		ArrayList<String> protocols = new ArrayList<>();

		try {
			List<PluginObjectContainer<AbstractIssuer>> loadedIssuers =
				CommonPluginUtils.getPluginObjectsInContainer(this, "master", "ProtocolExtension", "IssuerClass");
			for (PluginObjectContainer<AbstractIssuer> container : loadedIssuers) {
				String protocolName = 
					container.getPluginExtension().getDeclaringPluginDescriptor().getAttribute("ProtocolName").getValue();
				Class<?> issuerClass = container.getPluginClass();
				if (!issuersMap.containsKey(issuerClass)) {

					// hackish way to allow us to have an AbstractBasicIssuer.
					Class<?> superClass = issuerClass.getSuperclass();
					if (superClass != AbstractIssuer.class) {
						issuerClass = superClass;
					}
					issuersMap.put(issuerClass, container.getPluginObject());
				}

				if (!protocols.contains(protocolName)) {
					protocols.add(protocolName);
				}
			}
		} catch (IllegalArgumentException e) {
			logger.error("Failed to load plugins for master extension point 'ProtocolExtension', possibly the master"
					+" extension point definition has changed in the plugin.xml",e);
		}

		_issuersMap = Collections.unmodifiableMap(issuersMap);
		_protocols = Collections.unmodifiableList(protocols);

		logger.debug("Dumping issuers map");
		for (Entry<Class<?>, AbstractIssuer> e : _issuersMap.entrySet()) {
			Class<?> clazz = e.getKey();
			AbstractIssuer issuer = e.getValue();
            logger.debug("Class -> {}", clazz.toString());
            logger.debug("Issuer -> {}", issuer.toString());
		}
		
		for (String protocol : _protocols) {
            logger.debug("Protocol extension loaded: {}", protocol);
		}
	}
	
	/**
	 * Retrieves the Issuer instance for the given Class.
	 * @param clazz
	 * @return the Issuer instance for the given Class.
	 */
	public AbstractIssuer getIssuerForClass(Class<?> clazz) {
		return _issuersMap.get(clazz);
	}
	
	/**
	 * Whenever a slave connects, before it even start remerging, a "handshake" is started
	 * to check if the slave is capable of handling all operations that *might* be requested.
	 * @param rslave
	 * @throws ProtocolException Either if the slave isn't capable of handling all operations
	 * or there was an expected error during the handshake. 
	 */
	public void handshakeWithSlave(RemoteSlave rslave) throws ProtocolException {
		try {
			logger.debug("Trying to handshake with Slave");
			ObjectOutputStream out = rslave.getOutputStream();
			ObjectInputStream in = rslave.getInputStream();

			logger.debug("Writing protocol extensions to the socket.");
			out.writeObject(_protocols);
			out.flush();
			out.reset();

			logger.debug("Reading slave response.");
			HandshakeWrapper hw = (HandshakeWrapper) in.readObject();
			logger.debug("Slave response read");
			if (!hw.pluginStatus()) {
				logger.debug("There was an error during the handshake, check logs.", hw.getException());
				throw hw.getException();
			}
			logger.debug("Handshake successful");
		} catch (Exception e) {
			throw new ProtocolException(e);
		}
	}
}
