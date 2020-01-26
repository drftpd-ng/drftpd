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
package org.drftpd.slave;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.drftpd.PropertyHelper;
import org.drftpd.slave.async.*;

import java.io.EOFException;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * @author zubov
 * @version $Id$
 */
public class TestSlave extends Slave {
    private Socket _s;
    private ObjectInputStream _sin;
    private ObjectOutputStream _sout;
    private static final Logger logger = LogManager.getLogger(TestSlave.class);
	public TestSlave(Properties p) throws IOException {
        InetSocketAddress addr = new InetSocketAddress(PropertyHelper
				.getProperty(p, "master.host"), Integer.parseInt(PropertyHelper
				.getProperty(p, "master.bindport")));
        logger.info("Connecting to master at {}", addr);

		String slavename = PropertyHelper.getProperty(p, "slave.name");

		_s = new Socket();
		_s.connect(addr);

		_sout = new ObjectOutputStream(_s.getOutputStream());
		_sin = new ObjectInputStream(_s.getInputStream());

		// TODO sendReply()
		_sout.writeObject(slavename);
		_sout.flush();

	}
    public synchronized void sendResponse(AsyncResponse response) {
        if (response == null) {
            // handler doesn't return anything or it sends reply on it's own
            // (threaded for example)
            return;
        }

        try {
            _sout.writeObject(response);
            _sout.flush();
            if(!(response instanceof AsyncResponseTransferStatus)) {
                logger.debug("Slave wrote response - {}", response);
            }

            if (response instanceof AsyncResponseException) {
                logger.debug("",
                    ((AsyncResponseException) response).getThrowable());
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    public static void main(String[] args) throws Exception {
        //BasicConfigurator.configure();
        System.out.println(
            "DrFTPD Slave starting, further logging will be done through log4j");

        Properties p = new Properties();
        p.put("master.host", "localhost");
        p.put("master.bindport","1099");
        p.put("slave.name", "testslave");

        TestSlave s = new TestSlave(p);
        s.sendResponse(new AsyncResponseDiskStatus(new DiskStatus(0,0)));
        s.listenForCommands();
    }
    private void listenForCommands() throws IOException {
        while (true) {
            AsyncCommandArgument ac;

            try {
                ac = (AsyncCommandArgument) _sin.readObject();

                if (ac == null) {
                    continue;
                }
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            } catch (EOFException e) {
            	logger.debug("Master shutdown or went offline");
            	return;
            }

            logger.debug("Slave fetched {}", ac);
            class AsyncCommandHandler implements Runnable {
                private AsyncCommandArgument _command = null;

                public AsyncCommandHandler(AsyncCommandArgument command) {
                    _command = command;
                }

                public void run() {
                    try {
                        sendResponse(handleCommand(_command));
                    } catch (Throwable e) {
                        sendResponse(new AsyncResponseException(
                                _command.getIndex(), e));
                    }
                }
            }
            new Thread(new AsyncCommandHandler(ac)).start();
        }
    }
    private AsyncResponse handleCommand(AsyncCommandArgument ac) {
        if (ac.getName().equals("remerge")) {
        	List<LightRemoteInode> mergeFiles = new ArrayList<>();
        	
        	mergeFiles.add(new LightRemoteInode("ps2dvd",System.currentTimeMillis(), 0));
        	sendResponse(new AsyncResponseRemerge("/", mergeFiles, System.currentTimeMillis()));
        	mergeFiles.clear();
        	mergeFiles.add(new LightRemoteInode("c4testsagain",System.currentTimeMillis(), 100));
        	sendResponse(new AsyncResponseRemerge("\\ps2dvd", mergeFiles, System.currentTimeMillis()));
        	return new AsyncResponse(ac.getIndex());
        }

        if (ac.getName().equals("delete")) {
            return new AsyncResponse(ac.getIndex());
        }

        if (ac.getName().equals("maxpath")) {
            return new AsyncResponseMaxPath(ac.getIndex(),255);
        }

        if (ac.getName().equals("ping")) {
            return new AsyncResponse(ac.getIndex());
        }

        if (ac.getName().equals("rename")) {
        	return new AsyncResponse(ac.getIndex());
        }

        if (ac.getName().equals("error")) {
            throw new RuntimeException("error - " + ac);
        }

        return new AsyncResponseException(ac.getIndex(),
            new Exception(ac.getName() + " - Operation Not Supported"));
    }


}
