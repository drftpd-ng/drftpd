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
package org.drftpd.plugins;

import net.sf.drftpd.event.Event;
import net.sf.drftpd.event.FtpListener;
import net.sf.drftpd.event.TransferEvent;
import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.apache.log4j.Logger;

import org.drftpd.PropertyHelper;
import org.drftpd.sections.SectionInterface;

import java.io.FileInputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Properties;


/**
 * @author zubov
 *
 * @version $Id: Mirror.java,v 1.2 2004/11/09 18:59:56 mog Exp $
 */
public class Mirror implements FtpListener {
    private static final Logger logger = Logger.getLogger(Mirror.class);
    private ConnectionManager _cm;
    private ArrayList _exemptList;
    private boolean _mirrorAllSFV;
    private int _numberOfMirrors;

    public Mirror() {
        reload();
        logger.info("Mirror plugin loaded successfully");
    }

    public void actionPerformed(Event event) {
        if (event.getCommand().equals("RELOAD")) {
            reload();

            return;
        }

        if (event.getCommand().equals("STOR")) {
            actionPerformedSTOR((TransferEvent) event);
        }
    }

    public void actionPerformedSTOR(TransferEvent transevent) {
        if (!transevent.isComplete()) {
            return;
        }

        LinkedRemoteFileInterface file = transevent.getDirectory();

        if (checkExclude(_cm.getGlobalContext().getSectionManager().lookup(file.getPath()))) {
            return;
        }

        int numToMirror = _numberOfMirrors;

        if (_mirrorAllSFV && file.getName().toLowerCase().endsWith(".sfv")) {
            numToMirror = _cm.getGlobalContext().getSlaveManager().getSlaves()
                             .size() / 2;
        }

        _cm.getJobManager().addJobToQueue(new Job(file,
                _cm.getGlobalContext().getSlaveManager().getSlaves(), 0,
                numToMirror));
        logger.info("Done adding " + file.getPath() + " to the JobList");
    }

    /**
     * @param lrf
     * Returns true if lrf.getPath() is excluded
     */
    public boolean checkExclude(SectionInterface section) {
        return _exemptList.contains(section.getName());
    }

    public void init(ConnectionManager connectionManager) {
        _cm = connectionManager;
        _cm.getGlobalContext().loadJobManager();
    }

    private void reload() {
        Properties props = new Properties();

        try {
            props.load(new FileInputStream("conf/mirror.conf"));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        _numberOfMirrors = Integer.parseInt(PropertyHelper.getProperty(props,
                    "numberOfMirrors"));
        _mirrorAllSFV = PropertyHelper.getProperty(props, "mirrorAllSFV").equals("true");
        _exemptList = new ArrayList();

        for (int i = 1;; i++) {
            String path = props.getProperty("exclude." + i);

            if (path == null) {
                break;
            }

            _exemptList.add(path);
        }
    }

    public void unload() {
    }
}
