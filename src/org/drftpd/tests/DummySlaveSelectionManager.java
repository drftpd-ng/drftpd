package org.drftpd.tests;

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

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

/**
 * @author zubov
 * @version $Id: DummySlaveSelectionManager.java,v 1.1 2004/07/13 14:52:10 zubov Exp $
 */
public class DummySlaveSelectionManager
		implements
			SlaveSelectionManagerInterface {
	public DummySlaveSelectionManager() {
		super();
		// TODO Auto-generated constructor stub
	}
	public void reload() throws FileNotFoundException, IOException {
		// TODO Auto-generated method stub
	}
	public RemoteSlave getASlave(Collection rslaves, char direction,
			BaseFtpConnection conn, LinkedRemoteFileInterface file)
			throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}
	public RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file,
			FtpConfig cfg) throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}
	public SlaveManagerImpl getSlaveManager() {
		// TODO Auto-generated method stub
		return null;
	}
	public RemoteSlave getASlaveForJobDownload(Job job)
			throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}
	public RemoteSlave getASlaveForJobUpload(Job job)
			throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}
}
