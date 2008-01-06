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

import org.drftpd.GlobalContext;
import org.drftpd.master.SlaveManager;
import org.drftpd.sections.SectionManagerInterface;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.usermanager.AbstractUserManager;


/**
 * @author mog
 * @version $Id$
 */
public class DummyGlobalContext extends GlobalContext {
    public void setSectionManager(SectionManagerInterface manager) {
        _sectionManager = manager;
    }

    public void setSlaveManager(SlaveManager slavem) {
        _slaveManager = slavem;
    }

    public void setUserManager(AbstractUserManager um) {
        _usermanager = um;
    }

    public void setSlaveSelectionManager(SlaveSelectionManagerInterface dssm) {
        _slaveSelectionManager = dssm;
    }
}
