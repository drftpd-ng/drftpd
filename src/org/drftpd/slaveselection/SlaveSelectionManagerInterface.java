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
package org.drftpd.slaveselection;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.SlaveManagerImpl;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id: SlaveSelectionManagerInterface.java,v 1.4 2004/03/06 00:39:47 zubov Exp $
 */
public interface SlaveSelectionManagerInterface {
	public abstract void reload() throws FileNotFoundException, IOException;
	/**
	 * Checksums call us with null BaseFtpConnection.
	 */
	public abstract RemoteSlave getASlave(
		Collection rslaves,
		char direction,
		BaseFtpConnection conn,
		LinkedRemoteFileInterface file)
		throws NoAvailableSlaveException;
	/**
	 * Get slave for transfer to master.
	 */
	public abstract RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file, FtpConfig cfg)
		throws NoAvailableSlaveException;
	public abstract SlaveManagerImpl getSlaveManager();
	public abstract RemoteSlave getASlaveForJobDownload(Job job)
		throws NoAvailableSlaveException;
	public abstract RemoteSlave getASlaveForJobUpload(Job job)
		throws NoAvailableSlaveException;

}