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
package net.sf.drftpd.master;

import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;

import net.sf.drftpd.FatalException;
import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.ObjectNotFoundException;
import net.sf.drftpd.SlaveUnavailableException;
import net.sf.drftpd.event.SlaveEvent;
import net.sf.drftpd.master.usermanager.UserFileException;
import net.sf.drftpd.remotefile.LinkedRemoteFile;
import net.sf.drftpd.remotefile.MLSTSerialize;
import net.sf.drftpd.slave.Slave;
import net.sf.drftpd.slave.SlaveStatus;
import net.sf.drftpd.util.SafeFileWriter;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import org.drftpd.GlobalContext;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;

import java.lang.reflect.Constructor;

import java.net.InetAddress;

import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.RMISocketFactory;
import java.rmi.server.RemoteServer;
import java.rmi.server.UnicastRemoteObject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Properties;
import java.util.Set;


/**
 * @author mog
 * @version $Id: SlaveManagerImpl.java,v 1.105 2004/09/25 03:48:35 mog Exp $
 */
public class SlaveManagerImpl extends UnicastRemoteObject
    implements SlaveManager {
    private static final Logger logger = Logger.getLogger(SlaveManagerImpl.class.getName());
    private String slavePath = "slaves/";
    private File slavePathFile = new File(slavePath);

    ///**
    // * @deprecated
    // */
    //	public static void saveFilesXML(Element root) {
    //		File filesDotXml = new File("files.xml");
    //		File filesxmlbak = new File("files.xml.bak");
    //		filesxmlbak.delete();
    //		filesDotXml.renameTo(filesxmlbak);
    //		try {
    //			FileWriter out = new FileWriter(filesDotXml);
    //			new XMLOutputter("  ", true).output(root, out);
    //			out.flush();
    //		} catch (IOException ex) {
    //			logger.log(
    //				Level.WARN,
    //				"Error saving to " + filesDotXml.getPath(),
    //				ex);
    //		}
    //	}
    //protected ConnectionManager _cm;
    protected GlobalContext _gctx;
    protected List _rslaves;
    protected RMIServerSocketFactory _ssf;
    protected RMIClientSocketFactory _csf;
    protected SlaveSelectionManagerInterface _slaveSelectionManager;

    public SlaveManagerImpl() throws RemoteException {
        _rslaves = new ArrayList();
    }

    /**
     * Checksums call us with null BaseFtpConnection.
     */

    //	public static RemoteSlave getASlave(
    //		Collection slaves,
    //		char direction,
    //		FtpConfig config,
    //		BaseFtpConnection conn,
    //		LinkedRemoteFileInterface file)
    //		throws NoAvailableSlaveException {
    //		return config.getSlaveManager().getSlaveSelectionManager().getASlave(
    //			slaves,
    //			direction,
    //			conn,
    //			file);
    //	}
    //	public static Collection getAvailableSlaves()
    //		throws NoAvailableSlaveException {
    //		ArrayList availableSlaves = new ArrayList();
    //		for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
    //			RemoteSlave rslave = (RemoteSlave) iter.next();
    //			if (!rslave.isAvailable())
    //				continue;
    //			availableSlaves.add(rslave);
    //		}
    //		if (availableSlaves.isEmpty()) {
    //			throw new NoAvailableSlaveException("No slaves online");
    //		}
    //		return availableSlaves;
    //	}
    //	public static RemoteSlave loadRSlave(Element slaveElement) {
    //		List masks = new ArrayList();
    //		List maskElements = slaveElement.getChildren("mask");
    //		for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
    //			masks.add(((Element) i2.next()).getText());
    //		}
    //		return new RemoteSlave(slaveElement);
    //			slaveElement.getChildText("name").toString(),
    //			masks,
    //			slaveElement);
    //	}

    /*        private Properties elementToProps(Element config) {
                    Properties props = new Properties();
                    String masks = "";
                    List maskElements = config.getChildren("mask");
                    for (Iterator i2 = maskElements.iterator(); i2.hasNext();) {
                            if (!masks.equals(""))
                                    masks += ",";
                            masks += ((Element) i2.next()).getText();
                    }
                    props.put("masks", masks);
                    for (Iterator i = config.getChildren().iterator(); i.hasNext();) {
                            Element e = (Element) i.next();
                            if (e.getName().equalsIgnoreCase("mask"))
                                    continue;
                            try {
                                    props.setProperty(e.getName(), e.getText());
                            } catch (Exception e1) {
                            }
                    }
                    return props;
            }
    */
    public void loadSlaves() throws SlaveFileException {
        _rslaves = new ArrayList();

        if (!slavePathFile.exists() && !slavePathFile.mkdirs()) {
            throw new SlaveFileException(new IOException(
                    "Error creating folders: " + slavePathFile));
        }

        String[] slavepaths = slavePathFile.list();

        for (int i = 0; i < slavepaths.length; i++) {
            String slavepath = slavepaths[i];

            if (!slavepath.endsWith(".xml")) {
                continue;
            }

            String slavename = slavepath.substring(0,
                    slavepath.length() - ".xml".length());
            getSlaveByNameUnchecked(slavename);

            // throws IOException
        }

        Collections.sort(_rslaves);
    }

    public void addSlave(RemoteSlave rslave) {
        _rslaves.add(rslave);
        Collections.sort(_rslaves);
    }

    private RemoteSlave getSlaveByNameUnchecked(String slavename) {
        try {
            RemoteSlave rslave = null;
            XStream inp = new XStream(new DomDriver());
            FileReader in;
            in = new FileReader(getSlaveFile(slavename));

            try {
                rslave = (RemoteSlave) inp.fromXML(in);
                rslave.init(slavename, this);

                //throws RuntimeException
                _rslaves.add(rslave);

                return rslave;
            } catch (Exception e) {
                throw new FatalException(e);
            }
        } catch (Throwable ex) {
            throw new RuntimeException("Could not load slave " + slavename, ex);
        }
    }

    protected File getSlaveFile(String slavename) {
        return new File(slavePath + slavename + ".xml");
    }

    public Collection getMasks() {
        ArrayList masks = new ArrayList();

        for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave2 = (RemoteSlave) iter.next();
            masks.addAll(rslave2.getMasks());
        }

        return masks;
    }

    public void init(Properties cfg, RMIServerSocketFactory ssf,
        GlobalContext gctx) throws RemoteException {
        _csf = RMISocketFactory.getSocketFactory();
        _ssf = ssf;
        _gctx = gctx;

        Registry registry = LocateRegistry.createRegistry(Integer.parseInt(
                    cfg.getProperty("master.bindport", "1099")), _csf, _ssf);

        // throws RemoteException
        try {
            registry.bind(cfg.getProperty("master.bindname", "slavemanager"),
                this);
        } catch (Exception t) {
            throw new FatalException(t);
        }

        try {
            Constructor c = Class.forName(cfg.getProperty("slaveselection",
                        "org.drftpd.slaveselection.def.SlaveSelectionManager"))
                                 .getConstructor(new Class[] { GlobalContext.class });
            _slaveSelectionManager = (SlaveSelectionManagerInterface) c.newInstance(new Object[] {
                        getGlobalContext()
                    });
        } catch (Exception e) {
            if (e instanceof RuntimeException) {
                throw (RuntimeException) e;
            }

            throw new FatalException(e);
        }

        logger.debug("starting slavestatus updater thread");
        new SlaveStatusUpdater().start();
    }

    protected void addShutdownHook() {
        //add shutdown hook last
        Runtime.getRuntime().addShutdownHook(new Thread() {
                public void run() {
                    logger.info("Running shutdown hook");
                    saveFilelist();

                    try {
                        getGlobalContext().getUserManager().saveAll();
                    } catch (UserFileException e) {
                        logger.warn("", e);
                    }
                }
            });
    }

    public void mergeSlaveAndSetOnline(String slaveName, Slave slave,
        SlaveStatus status, int maxPath) throws RemoteException {
        slave.ping();

        RemoteSlave rslave = null;

        for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave2 = (RemoteSlave) iter.next();

            if (rslave2.getName().equals(slaveName)) {
                rslave = rslave2;

                break;
            }
        }

        if (rslave == null) {
            try {
                rslave = getSlaveByNameUnchecked(slaveName);
            } catch (Exception e2) {
                // do nothing since we want to throw IllegalArgumentException
            }

            if (rslave == null) {
                throw new IllegalArgumentException(
                    "Slave not found in slaves.xml");
            }
        }

        if (rslave.isAvailablePing()) {
            throw new IllegalArgumentException(rslave.getName() +
                " is already online");
        }

        try {
            InetAddress addr = null;

            /*                        if (slave instanceof SocketSlaveImpl) {
                                            addr = ((SocketSlaveImpl) slave).getPeerAddress();
                                    }
            */
            if (addr == null) {
                addr = InetAddress.getByName(RemoteServer.getClientHost());
            }

            //logger.debug("slave ip address is " + addr);
            if (addr == null) {
                throw new IllegalArgumentException(rslave.getName() +
                    " has no slave address");
            }

            try {
                rslave.setSlave(slave, addr, slave.getSlaveStatus(), maxPath);
            } catch (RemoteException e) {
                logger.warn("", e);
                rslave.setOffline("IOException during remerge()");

                return;
            }
        } catch (Throwable e1) {
            throw new FatalException(e1);
        }

        logger.debug("About to remerge(), slave is " + rslave);

        try {
            remerge(rslave);
        } catch (IOException e) {
            logger.warn("", e);
            rslave.setOffline("IOException during remerge()");

            return;
        } catch (SlaveUnavailableException e) {
            logger.warn("", e);
            rslave.setOffline("Slave Unavailable during remerge()");

            return;
        }

        rslave.setAvailable(true);
        logger.info("Slave added: '" + rslave.getName() + "' status: " +
            status);
        getGlobalContext().getConnectionManager().dispatchFtpEvent(new SlaveEvent(
                "ADDSLAVE", rslave));
    }

    public void delSlave(String slaveName) {
        RemoteSlave rslave = null;

        try {
            rslave = getSlave(slaveName);
        } catch (ObjectNotFoundException e) {
            throw new IllegalArgumentException("Slave not found");
        } finally {
            getSlaveFile(rslave.getName()).delete();
            rslave.setOffline("Slave has been deleted");
            _rslaves.remove(rslave);
            getGlobalContext().getRoot().unmergeDir(rslave);
        }
    }

    public HashSet findSlavesBySpace(int numOfSlaves, Set exemptSlaves,
        boolean ascending) {
        Collection slaveList = getGlobalContext().getSlaveManager().getSlaves();
        HashMap map = new HashMap();

        for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (exemptSlaves.contains(rslave)) {
                continue;
            }

            Long size;

            try {
                size = new Long(rslave.getStatusAvailable()
                                      .getDiskSpaceAvailable());
            } catch (SlaveUnavailableException e) {
                continue;
            }

            map.put(size, rslave);
        }

        ArrayList sorted = new ArrayList(map.keySet());

        if (ascending) {
            Collections.sort(sorted);
        } else {
            Collections.sort(sorted, Collections.reverseOrder());
        }

        HashSet returnMe = new HashSet();

        for (ListIterator iter = sorted.listIterator(); iter.hasNext();) {
            if (iter.nextIndex() == numOfSlaves) {
                break;
            }

            Long key = (Long) iter.next();
            RemoteSlave rslave = (RemoteSlave) map.get(key);
            returnMe.add(rslave);
        }

        return returnMe;
    }

    public RemoteSlave findSmallestFreeSlave() {
        Collection slaveList = getGlobalContext().getConnectionManager()
                                   .getGlobalContext().getSlaveManager()
                                   .getSlaves();
        long smallSize = Integer.MAX_VALUE;
        RemoteSlave smallSlave = null;

        for (Iterator iter = slaveList.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();
            long size = Integer.MAX_VALUE;

            try {
                size = rslave.getStatusAvailable().getDiskSpaceAvailable();
            } catch (SlaveUnavailableException e) {
                continue;
            }

            if (size < smallSize) {
                smallSize = size;
                smallSlave = rslave;
            }
        }

        return smallSlave;
    }

    /**
     * Not cached at all since RemoteSlave objects cache their SlaveStatus
     */
    public SlaveStatus getAllStatus() {
        SlaveStatus allStatus = new SlaveStatus();

        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            try {
                allStatus = allStatus.append(rslave.getStatusAvailable());
            } catch (SlaveUnavailableException e) {
                //slave is offline, continue
            }
        }

        return allStatus;
    }

    public HashMap getAllStatusArray() {
        //SlaveStatus[] ret = new SlaveStatus[getSlaves().size()];
        HashMap ret = new HashMap(getSlaves().size());

        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            try {
                ret.put(rslave.getName(), rslave.getStatus());
            } catch (SlaveUnavailableException e) {
                ret.put(rslave.getName(), (Object) null);
            }
        }

        return ret;
    }

    //	private Random rand = new Random();
    //	public RemoteSlave getASlave() {
    //		ArrayList retSlaves = new ArrayList();
    //		for (Iterator iter = this.rslaves.iterator(); iter.hasNext();) {
    //			RemoteSlave rslave = (RemoteSlave) iter.next();
    //			if (!rslave.isAvailable())
    //				continue;
    //			retSlaves.add(rslave);
    //		}
    //
    //		int num = rand.nextInt(retSlaves.size());
    //		logger.fine(
    //			"Slave "
    //				+ num
    //				+ " selected out of "
    //				+ retSlaves.size()
    //				+ " available slaves");
    //		return (RemoteSlave) retSlaves.get(num);
    //	}
    public Collection getAvailableSlaves() throws NoAvailableSlaveException {
        ArrayList availableSlaves = new ArrayList();

        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (!rslave.isAvailable()) {
                continue;
            }

            availableSlaves.add(rslave);
        }

        if (availableSlaves.isEmpty()) {
            throw new NoAvailableSlaveException("No slaves online");
        }

        return availableSlaves;
    }

    public GlobalContext getGlobalContext() {
        if (_gctx == null) {
            throw new NullPointerException();
        }

        return _gctx;
    }

    public RemoteSlave getSlave(String s) throws ObjectNotFoundException {
        for (Iterator iter = getSlaves().iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (rslave.getName().equals(s)) {
                return rslave;
            }
        }

        throw new ObjectNotFoundException(s + ": No such slave");
    }

    public List getSlaves() {
        if (_rslaves == null) {
            throw new NullPointerException();
        }

        return Collections.unmodifiableList(_rslaves);
    }

    public SlaveSelectionManagerInterface getSlaveSelectionManager() {
        return _slaveSelectionManager;
    }

    /**
     * @deprecated Use RemoteSlave.handleRemoteException instead
     */
    public void handleRemoteException(RemoteException ex, RemoteSlave rslave) {
        rslave.handleRemoteException(ex);
    }

    /**
     * Returns true if one or more slaves are online, false otherwise.
     * @return true if one or more slaves are online, false otherwise.
     */
    public boolean hasAvailableSlaves() {
        for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
            RemoteSlave rslave = (RemoteSlave) iter.next();

            if (rslave.isAvailable()) {
                return true;
            }
        }

        return false;
    }

    public void reload() throws FileNotFoundException, IOException {
        _slaveSelectionManager.reload();

        // removed with slaves.xml - reloadRSlaves();
    }

    /*        public void reloadRSlaves() throws FileNotFoundException, IOException {
                    Document doc;
                    try {
                            doc = new SAXBuilder().build(new FileReader("conf/slaves.xml"));
                    } catch (JDOMException e) {
                            throw (IOException) new IOException().initCause(e);
                    }

                    List slaveElements = doc.getRootElement().getChildren("slave");

                    // first, unmerge non-existing slaves
                    synchronized (_rslaves) {
                            nextslave : for (
                                    Iterator iter = _rslaves.iterator(); iter.hasNext();) {
                                    RemoteSlave rslave = (RemoteSlave) iter.next();

                                    for (Iterator iterator = slaveElements.iterator();
                                            iterator.hasNext();
                                            ) {
                                            Element slaveElement = (Element) iterator.next();
                                            if (rslave
                                                    .getName()
                                                    .equals(slaveElement.getChildText("name"))) {
                                                    logger.log(
                                                            Level.DEBUG,
                                                            rslave.getName() + " still in slaves.xml");
                                                    continue nextslave;
                                            }
                                    }
                                    logger.log(
                                            Level.WARN,
                                            rslave.getName() + " no longer in slaves.xml, unmerging");
                                    rslave.setOffline("Slave removed from slaves.xml");
                                    getGlobalContext().getConnectionManager().getGlobalContext().getRoot().unmergeDir(rslave);
                                    //rslaves.remove(rslave);
                                    iter.remove();
                            }
                    }

                    nextelement : for (
                            Iterator iterator = slaveElements.iterator();
                                    iterator.hasNext();
                                    ) {
                            Element slaveElement = (Element) iterator.next();

                            for (Iterator iter = _rslaves.iterator(); iter.hasNext();) {
                                    RemoteSlave rslave = (RemoteSlave) iter.next();

                                    if (slaveElement
                                            .getChildText("name")
                                            .equals(rslave.getName())) {
                                            rslave.updateConfig(elementToProps(slaveElement));
                                            //                                        List masks = new ArrayList();
                                            //                                        List maskElements = slaveElement.getChildren("mask");
                                            //                                        for (Iterator i2 = maskElements.iterator();
                                            //                                                i2.hasNext();
                                            //                                                ) {
                                            //                                                masks.add(((Element) i2.next()).getText());
                                            //                                        }
                                            //                                        rslave.setMasks(masks);
                                            continue nextelement;
                                    }
                            } // rslaves.iterator()
                            RemoteSlave rslave = new RemoteSlave(elementToProps(slaveElement));
                            rslave.setManager(this);
                            _rslaves.add(rslave);
                            logger.log(Level.INFO, "Added " + rslave.getName() + " to slaves");
                    }
                    Collections.sort(_rslaves);
            }
    */
    public void remerge(RemoteSlave rslave)
        throws IOException, SlaveUnavailableException {
        LinkedRemoteFile slaveroot;
        slaveroot = rslave.getSlaveRoot();

        try {
            getGlobalContext().getConnectionManager().getGlobalContext()
                .getRoot().remerge(slaveroot, rslave);
        } catch (RuntimeException t) {
            logger.log(Level.FATAL, "", t);
            rslave.setOffline(t.getMessage());
            throw t;
        }
    }

    public void saveFilelist() {
        try {
            SafeFileWriter out = new SafeFileWriter("files.mlst");

            try {
                MLSTSerialize.serialize(getGlobalContext().getConnectionManager()
                                            .getGlobalContext().getRoot(), out);
            } finally {
                out.close();
            }
        } catch (IOException e) {
            logger.warn("Error saving files.mlst", e);
        }
    }

    /** ping's all slaves, returns number of slaves removed */
    public int verifySlaves() {
        int removed = 0;

        synchronized (_rslaves) {
            for (Iterator i = _rslaves.iterator(); i.hasNext();) {
                RemoteSlave slave = (RemoteSlave) i.next();

                if (!slave.isAvailablePing()) {
                    removed++;
                }
            }
        }

        return removed;
    }

    public class SlaveStatusUpdater extends Thread {
        public SlaveStatusUpdater() {
            super("SlaveStatusUpdater");
        }

        public void run() {
            logger.debug("started slavestatus updater thread");

            long low = Integer.MAX_VALUE;
            long high = 0;

            while (true) {
                try {
                    for (Iterator iter = getAvailableSlaves().iterator();
                            iter.hasNext();) {
                        RemoteSlave slave = (RemoteSlave) iter.next();

                        try {
                            long time = System.currentTimeMillis();
                            slave.updateStatus();

                            long difference = System.currentTimeMillis() -
                                time;

                            if (difference < low) {
                                low = difference;
                                logger.debug(low +
                                    " low milliseconds were used to run updateStatus on " +
                                    slave.getName());
                            }

                            if (difference > high) {
                                high = difference;
                                logger.debug(high +
                                    " high milliseconds were used to run updateStatus on " +
                                    slave.getName());
                            }
                        } catch (SlaveUnavailableException e1) {
                            continue;
                        }
                    }
                } catch (NoAvailableSlaveException e) {
                }

                try {
                    Thread.sleep(getGlobalContext().getConfig()
                                     .getSlaveStatusUpdateTime());
                } catch (InterruptedException e1) {
                }
            }
        }
    }

    /*        public void updateSlave(String name, Hashtable args) throws IOException {
                    Element tmp;

                    String mask = (String) args.get("mask");
                    String skey = (String) args.get("skey");
                    String mkey = (String) args.get("mkey");
                    String port = (String) args.get("port");
                    String addr = (String) args.get("addr");

                    // create new slaves.xml entry
                    Element slave = new Element("slave");

                    tmp = new Element("name");
                    tmp.setText(name);
                    slave.addContent(tmp);

                    tmp = new Element("addr");
                    if (addr == null) {
                            tmp.setText("Dynamic");
                    } else {
                            tmp.setText(addr);
                    }
                    slave.addContent(tmp);

                    if (mask != null) {
                            String[] masks = mask.split(",");
                            for (int i = 0; i < masks.length; i++) {
                                    tmp = new Element("mask");
                                    tmp.setText(masks[i]);
                                    slave.addContent(tmp);
                            }
                    }

                    if (port != null) {
                            tmp = new Element("port");
                            tmp.setText(port);
                            slave.addContent(tmp);
                    }

                    if (skey != null) {
                            tmp = new Element("slavepass");
                            tmp.setText(skey);
                            slave.addContent(tmp);
                    }

                    if (mkey != null) {
                            tmp = new Element("masterpass");
                            tmp.setText(mkey);
                            slave.addContent(tmp);
                    }

                    // get the current slaves.xml file
                    Document doc;
                    try {
                            doc = new SAXBuilder().build(new FileReader("conf/slaves.xml"));
                    } catch (JDOMException e) {
                            throw (IOException) new IOException().initCause(e);
                    }

                    // get the current list of slaves
                    List slaveElements = doc.getRootElement().getChildren("slave");

                    // try to find an existing slave
                    Element conf = null;
                    for (Iterator iterator = slaveElements.iterator();
                            iterator.hasNext();
                            ) {
                            Element slaveElement = (Element) iterator.next();
                            if (name.equals(slaveElement.getChildText("name"))) {
                                    conf = slaveElement;
                            }
                    }

                    if (conf == null) {
                            // create new slave entry
                            doc.getRootElement().addContent(slave);
                    } else {
                            // update existing entry
                            slaveElements.remove(conf);
                            slaveElements.add(slave);
                            doc.setContent(slaveElements);
                    }

                    // write slaves.xml
                    XMLOutputter out = new XMLOutputter("  ", true);
                    out.output(doc, new FileWriter("conf/slaves.xml"));
                    out = null;

                    // trigger the normal slave reload process
                    reloadRSlaves();

            }*/
}
