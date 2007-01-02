package org.drftpd.tests;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.mirroring.Job;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.SlaveSelectionManagerInterface;
import org.drftpd.vfs.FileHandle;
import org.drftpd.vfs.InodeHandle;


/**
 * @author zubov
 * @version $Id$
 */
public class DummySlaveSelectionManager
    implements SlaveSelectionManagerInterface {
    public DummySlaveSelectionManager() {
        super();
    }

    public void reload() throws FileNotFoundException, IOException {
    }

    public RemoteSlave getASlaveForMaster(FileHandle file,
        ConfigInterface cfg) throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public GlobalContext getGlobalContext() {
        throw new UnsupportedOperationException();
    }

	public RemoteSlave getASlave(Collection<RemoteSlave> rslaves, char direction, BaseFtpConnection conn, InodeHandle file) throws NoAvailableSlaveException {
		// TODO Auto-generated method stub
		return null;
	}
}
