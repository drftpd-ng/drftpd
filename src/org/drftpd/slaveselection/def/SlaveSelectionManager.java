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
package org.drftpd.slaveselection.def;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window&gt;Preferences&gt;Java&gt;Code Generation&gt;Code and Comments
 */
public class SlaveSelectionManager implements SlaveSelectionManagerInterface {

	/**
	 * 
	 */
	public SlaveSelectionManager() {
		super();
		// TODO Auto-generated constructor stub
	}

	/* (non-Javadoc)
	 * @see org.drftpd.slaveselection.SlaveSelectionManagerInterface#reload()
	 */
	public void reload() throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub

	}

	/* (non-Javadoc)
	 * @see org.drftpd.slaveselection.SlaveSelectionManagerInterface#getASlave(java.util.Collection, char, net.sf.drftpd.master.BaseFtpConnection, net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
	 */
	public RemoteSlave getASlave(
		Collection rslaves,
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.drftpd.slaveselection.SlaveSelectionManagerInterface#getASlave(net.sf.drftpd.remotefile.LinkedRemoteFileInterface)
	 */
	public RemoteSlave getASlave(LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.drftpd.slaveselection.SlaveSelectionManagerInterface#getSlaveManager()
	 */
	public SlaveManagerImpl getSlaveManager() {
		// TODO Auto-generated method stub
		return null;
	}

	/* (non-Javadoc)
	 * @see org.drftpd.slaveselection.SlaveSelectionManagerInterface#getASlave(net.sf.drftpd.mirroring.Job, net.sf.drftpd.master.RemoteSlave)
	 */
	public RemoteSlave getASlave(Job temp, RemoteSlave destslave)
		throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}

}
