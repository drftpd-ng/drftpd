package org.drftpd.tests;

import org.drftpd.exceptions.NoAvailableSlaveException;
import org.drftpd.master.BaseFtpConnection;
import org.drftpd.master.RemoteSlave;
import org.drftpd.plugins.jobmanager.Job;
import org.drftpd.slaveselection.filter.SlaveSelectionManager;
import org.drftpd.vfs.InodeHandle;

import java.io.FileNotFoundException;
import java.io.IOException;


/**
 * @author zubov
 * @version $Id$
 */
public class DummySlaveSelectionManager extends SlaveSelectionManager {
    public DummySlaveSelectionManager() throws IOException {
		super();
	}

	public void reload() throws FileNotFoundException, IOException { }

    public RemoteSlave getASlaveForJobDownload(Job job) {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave) {
        throw new UnsupportedOperationException();
    }

	public RemoteSlave getASlave(BaseFtpConnection conn, char direction, InodeHandle file) throws NoAvailableSlaveException {
		throw new UnsupportedOperationException();
	}
}
