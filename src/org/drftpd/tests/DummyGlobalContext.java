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
package org.drftpd.tests;

import net.sf.drftpd.master.ConnectionManager;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.remotefile.LinkedRemoteFile;

import org.drftpd.GlobalContext;

import org.drftpd.sections.def.SectionManager;

import java.util.Properties;


/**
 * @author mog
 * @version $Id: DummyGlobalContext.java,v 1.2 2004/08/03 20:14:10 zubov Exp $
 */
public class DummyGlobalContext extends GlobalContext {
    public DummyGlobalContext() {
    }

    public void loadPlugins(Properties cfg) {
        super.loadPlugins(cfg);
    }

    public void loadUserManager(Properties cfg, String cfgFileName) {
        super.loadUserManager(cfg, cfgFileName);
    }

    public void setConnectionManager(ConnectionManager cm) {
        _cm = cm;
    }

    public void setFtpConfig(FtpConfig config) {
        _config = config;
    }

    public void setSectionManager(SectionManager manager) {
        _sections = manager;
    }

    public void setSlaveManager(DummySlaveManager slavem) {
        _slaveManager = slavem;
    }

    public void setUserManager(DummyUserManager um) {
        _usermanager = um;
    }

    public void setRoot(LinkedRemoteFile root) {
        _root = root;
    }
}
