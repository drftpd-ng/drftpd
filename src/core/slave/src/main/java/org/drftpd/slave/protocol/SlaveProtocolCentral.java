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
package org.drftpd.slave.protocol;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.drftpd.common.exceptions.AsyncResponseException;
import org.drftpd.common.network.AsyncCommandArgument;
import org.drftpd.common.network.AsyncResponse;
import org.drftpd.common.protocol.HandshakeWrapper;
import org.drftpd.common.protocol.ProtocolException;
import org.drftpd.slave.Slave;
import org.reflections.Reflections;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * SlaveProtocolCentral handles the load of all connected Handlers.<br>
 * These handlers represent a pluggable way of implementing different kind of
 * operations between Master and Slave.
 *
 * @author fr0w
 * @version $Id$
 */
public class SlaveProtocolCentral {
    private static final Logger logger = LogManager.getLogger(SlaveProtocolCentral.class);
    private static final Class<?>[] CONSTRUCTORPARMS = {SlaveProtocolCentral.class};
    private static final Class<?>[] METHODPARMS = {AsyncCommandArgument.class};
    public Map<String, HandlerWrapper> _handlersMap;
    public List<String> _protocols;
    private final Slave _slave;

    /**
     * Instantiate the Central and load all connected handlers.
     *
     * @param slave
     */
    public SlaveProtocolCentral(Slave slave) {
        _slave = slave;
        loadHandlers();
    }

    private static String decapitalize(String string) {
        if (string == null || string.length() == 0) {
            return string;
        }

        char[] c = string.toCharArray();
        c[0] = Character.toLowerCase(c[0]);

        return new String(c);
    }

    /**
     * Whenever the Slave connects to the master, it receives a List containing all ProtocolExtensions loaded by master.<br>
     * Slave will iterate through this List, checking if the requested extension is also loaded by the slave.<br>
     * After the checking is done, Slave writes a {@link HandlerWrapper} to the socket and let master handles the rest.
     *
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
                throw new RuntimeException("An error happened: " + ac.getArgs());
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
     * Loads all connected Handlers and make them available for later usage.
     */
    private void loadHandlers() {
        HashMap<String, HandlerWrapper> handlers = new HashMap<>();
        ArrayList<String> protocols = new ArrayList<>();

        Set<Class<? extends AbstractHandler>> aHandlers = new Reflections("org.drftpd")
                .getSubTypesOf(AbstractHandler.class);
        List<Class<? extends AbstractHandler>> handlersProtocols = aHandlers.stream()
                .filter(aClass -> !Modifier.isAbstract(aClass.getModifiers())).collect(Collectors.toList());
        try {
            for (Class<? extends AbstractHandler> handlerClass : handlersProtocols) {
                AbstractHandler abstractHandler = handlerClass.getConstructor(this.getClass()).newInstance(this);
                List<Method> handlerMethods = Arrays.stream(handlerClass.getMethods()).filter(method -> method.getName().startsWith("handle")).collect(Collectors.toList());
                for (Method handlerMethod : handlerMethods) {
                    String name = decapitalize(handlerMethod.getName().substring("handle".length()));
                    String protocolName = abstractHandler.getProtocolName();
                    if (!protocols.contains(protocolName)) {
                        protocols.add(protocolName);
                    }
                    handlers.put(name, new HandlerWrapper(abstractHandler, handlerMethod));
                }
            }
        } catch (Exception e) {
            logger.error("Failed to load plugins for slave extension point 'Handler', possibly the slave"
                    + " extension point definition has changed in the plugin.xml", e);
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
            return new AsyncResponseException(ac.getIndex(), new Exception(ac.getName() + " - Operation Not Supported"));
        }

        Method m = wrapper.getMethod();
        AbstractHandler ah = wrapper.getAsyncHandler();
        AsyncResponse ar;

        try {
            ar = (AsyncResponse) m.invoke(ah, new Object[]{ac});
        } catch (Exception e) {
            logger.error("Unable to invoke: {}", m.toGenericString(), e);
            logger.error("Invokation failed due to: {}", m.toGenericString(), e.getCause());
            return new AsyncResponseException(ac.getIndex(), new Exception("Unable to invoke the proper handler", e));
        }

        return ar;
    }
}
