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

import net.sf.drftpd.master.SlaveFileException;
import net.sf.drftpd.master.SlaveManager;

import org.drftpd.GlobalContext;

import java.util.List;


/**
 * @author mog
 * @version $Id: DummySlaveManager.java,v 1.6 2004/11/08 18:39:31 mog Exp $
 */
public class DummySlaveManager extends SlaveManager {
    public DummySlaveManager() throws SlaveFileException {
        super();
    }

    public void setSlaves(List rslaves) {
        _rslaves = rslaves;
    }

    public void setGlobalContext(GlobalContext gctx) {
        _gctx = gctx;
    }
}
