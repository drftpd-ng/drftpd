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
package org.drftpd.plugins;

import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.Properties;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.PropertyHelper;
import org.drftpd.master.ConnectionManager;
import org.drftpd.mirroring.ArchiveHandler;
import org.drftpd.mirroring.ArchiveType;
import org.drftpd.mirroring.DuplicateArchiveException;
import org.drftpd.sections.SectionInterface;

/**
 * @author zubov
 * @version $Id$
 */
public class Archive extends FtpListener implements Runnable {
    private static final Logger logger = Logger.getLogger(Archive.class);
    private Properties _props;
    private long _cycleTime;
    private boolean _isStopped = false;
    private Thread thread = null;
    private ArrayList<ArchiveHandler> _archiveHandlers = new ArrayList<ArchiveHandler>();
    public Archive() {
        logger.info("Archive plugin loaded successfully");
    }

    public Properties getProperties() {
        return _props;
    }

    public void actionPerformed(Event event) {
        if (event.getCommand().equals("RELOAD")) {
            reload();
            return;
        }
    }

    /**
     * @return the correct ArchiveType for the @section - it will return null if that section does not have an archiveType loaded for it
     */
    public ArchiveType getArchiveType(SectionInterface section) {
        Class[] classParams = {
                Archive.class, SectionInterface.class, Properties.class
            };
        ArchiveType archiveType = null;
        String name = null;

        try {
            name = PropertyHelper.getProperty(_props,
                    section.getName() + ".archiveType");
        } catch (NullPointerException e) {
            return null; // excluded, not setup
        }

        Constructor constructor = null;

        try {
            constructor = Class.forName("org.drftpd.mirroring.archivetypes." +
                    name).getConstructor(classParams);
        } catch (Exception e1) {
            throw new RuntimeException(
                "Unable to load ArchiveType for section " + section.getName(),
                e1);
        }

        Object[] objectParams = { this, section, _props };
        try {
            archiveType = (ArchiveType) constructor.newInstance(objectParams);
        } catch (Exception e2) {
            throw new RuntimeException(
                "Unable to load ArchiveType for section " + section.getName(),
                e2);
        }

        return archiveType;
    }

    /**
     * Returns the getCycleTime setting
     */
    public long getCycleTime() {
        return _cycleTime;
    }

    public void init(GlobalContext gctx) {
    	super.init(gctx);
        getGlobalContext().loadJobManager();
        reload();
        startArchive();
    }

    private boolean isStopped() {
        return _isStopped;
    }

    private void reload() {
        _props = new Properties();

        try {
            _props.load(new FileInputStream("conf/archive.conf"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        _cycleTime = 60000 * Long.parseLong(PropertyHelper.getProperty(_props,
                    "cycleTime"));
    }

    public void run() {
        while (true) {
            if (isStopped()) {
                logger.debug("Stopping ArchiveStarter thread");

                return;
            }

            Collection sectionsToCheck = getGlobalContext().getSectionManager()
					.getSections();

            for (Iterator iter = sectionsToCheck.iterator(); iter.hasNext();) {
                SectionInterface section = (SectionInterface) iter.next();
                ArchiveType archiveType = getArchiveType(section);

                if (archiveType == null) {
                    continue;
                }

                new ArchiveHandler(archiveType).start();
            }

            try {
                Thread.sleep(_cycleTime);
            } catch (InterruptedException e) {
            }
        }
    }

    public void startArchive() {
        if (thread != null) {
            stopArchive();
            thread.interrupt();

            while (thread.isAlive()) {
                Thread.yield();
            }
        }

        _isStopped = false;
        thread = new Thread(this, "ArchiveStarter");
        thread.start();
    }

    public void stopArchive() {
        _isStopped = true;
    }

    public void unload() {
        stopArchive();
    }

    public synchronized boolean removeArchiveHandler(ArchiveHandler handler) {
        for (Iterator iter = _archiveHandlers.iterator(); iter.hasNext();) {
            ArchiveHandler ah = (ArchiveHandler) iter.next();

            if (ah == handler) {
                iter.remove();

                return true;
            }
        }

        return false;
    }

    public Collection<ArchiveHandler> getArchiveHandlers() {
        return Collections.unmodifiableCollection(_archiveHandlers);
    }

    public synchronized void addArchiveHandler(ArchiveHandler handler)
        throws DuplicateArchiveException {
        checkPathForArchiveStatus(handler.getArchiveType().getDirectory()
                                         .getPath());
        _archiveHandlers.add(handler);
    }

    public void checkPathForArchiveStatus(String handlerPath)
        throws DuplicateArchiveException {
        for (Iterator iter = _archiveHandlers.iterator(); iter.hasNext();) {
            ArchiveHandler ah = (ArchiveHandler) iter.next();
            String ahPath = ah.getArchiveType().getDirectory().getPath();
            logger.debug("ahPath = " + ahPath);
            logger.debug("handlerPath = " + handlerPath);

            if (ahPath.length() > handlerPath.length()) {
                if (ahPath.startsWith(handlerPath)) {
                    throw new DuplicateArchiveException(ahPath +
                        " is already being archived");
                }
            } else {
                if (handlerPath.startsWith(ahPath)) {
                    throw new DuplicateArchiveException(handlerPath +
                        " is already being archived");
                }
            }
        }
    }
}
