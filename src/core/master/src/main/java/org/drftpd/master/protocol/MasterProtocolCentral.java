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
package org.drftpd.master.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.protocol.AbstractIssuer;
import org.drftpd.common.protocol.HandshakeWrapper;
import org.drftpd.common.protocol.ProtocolException;
import org.drftpd.master.slavemanagement.RemoteSlave;
import org.reflections.Reflections;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * MasterProtocolCentral handles the load of all connected Protocol Extensions,
 * these extensions represent a pluggable way of implementing different kind of
 * operations between Master and Slave.
 *
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
    public MasterProtocolCentral() {
        loadProtocolExtensions();
    }

    /**
     * Iterate through all connected extensions, loading them.
     */
    private void loadProtocolExtensions() {
        HashMap<Class<?>, AbstractIssuer> issuersMap = new HashMap<>();
        ArrayList<String> protocols = new ArrayList<>();
        // TODO [DONE] @k2r Add protocols
        Set<Class<? extends AbstractIssuer>> issuers = new Reflections("org.drftpd")
                .getSubTypesOf(AbstractIssuer.class);
        List<Class<? extends AbstractIssuer>> issuerProtocols = issuers.stream()
                .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers())).collect(Collectors.toList());
        try {
            for (Class<?> issuerClass : issuerProtocols) {
                AbstractIssuer abstractIssuer = (AbstractIssuer) issuerClass.getConstructor().newInstance();
                String protocolName = abstractIssuer.getProtocolName();
                Class<?> superClass = issuerClass.getSuperclass();
                if (superClass != AbstractIssuer.class) {
                    issuerClass = superClass;
                }
                if (!issuersMap.containsKey(issuerClass)) {
                    issuersMap.put(issuerClass, abstractIssuer);
                }
                if (!protocols.contains(protocolName)) {
                    protocols.add(protocolName);
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for master extension point 'ProtocolExtension', possibly the master"
                    + " extension point definition has changed in the plugin.xml", e);
        }

        _issuersMap = Collections.unmodifiableMap(issuersMap);
        _protocols = Collections.unmodifiableList(protocols);

        logger.trace("Dumping issuers map");
        for (Entry<Class<?>, AbstractIssuer> e : _issuersMap.entrySet()) {
            Class<?> clazz = e.getKey();
            AbstractIssuer issuer = e.getValue();
            logger.trace("Class -> {}", clazz.toString());
            logger.trace("Issuer -> {}", issuer.toString());
        }

        for (String protocol : _protocols) {
            logger.debug("Protocol extension loaded: {}", protocol);
        }
    }

    /**
     * Retrieves the Issuer instance for the given Class.
     *
     * @param clazz
     * @return the Issuer instance for the given Class.
     */
    public AbstractIssuer getIssuerForClass(Class<?> clazz) {
        return _issuersMap.get(clazz);
    }

    /**
     * Whenever a slave connects, before it even start remerging, a "handshake" is started
     * to check if the slave is capable of handling all operations that *might* be requested.
     *
     * @param rslave
     * @throws ProtocolException Either if the slave isn't capable of handling all operations
     *                           or there was an expected error during the handshake.
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
