package org.drftpd.tests;

import java.io.FileNotFoundException;
import java.io.IOException;

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.jobmanager.Job;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.slaveselection.filter.SlaveSelectionManager;
import org.drftpd.vfs.InodeHandle;


/**
 * @author zubov
 * @version $Id$
 */
public class DummySlaveSelectionManager extends SlaveSelectionManager {
    public DummySlaveSelectionManager() throws IOException {
		super();
	}

	public void reload() throws FileNotFoundException, IOException { }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

	public RemoteSlave getASlave(BaseFtpConnection conn, char direction, InodeHandle file) throws NoAvailableSlaveException {
		throw new UnsupportedOperationException();
	}
}
