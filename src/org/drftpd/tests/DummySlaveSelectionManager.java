package org.drftpd.tests;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.RemoteSlave;
import net.sf.drftpd.master.config.FtpConfig;
import net.sf.drftpd.mirroring.Job;
import net.sf.drftpd.remotefile.LinkedRemoteFileInterface;

import org.drftpd.GlobalContext;

import org.drftpd.slaveselection.SlaveSelectionManagerInterface;

import java.io.FileNotFoundException;
import java.io.IOException;

import java.util.Collection;


/**
 * @author zubov
 * @version $Id: DummySlaveSelectionManager.java,v 1.6 2004/11/03 16:46:49 mog Exp $
 */
public class DummySlaveSelectionManager
    implements SlaveSelectionManagerInterface {
    public DummySlaveSelectionManager() {
        super();
    }

    public void reload() throws FileNotFoundException, IOException {
    }

    public RemoteSlave getASlave(Collection rslaves, char direction,
        BaseFtpConnection conn, LinkedRemoteFileInterface file)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForMaster(LinkedRemoteFileInterface file,
        FtpConfig cfg) throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobDownload(Job job)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public RemoteSlave getASlaveForJobUpload(Job job)
        throws NoAvailableSlaveException {
        throw new UnsupportedOperationException();
    }

    public GlobalContext getGlobalContext() {
        throw new UnsupportedOperationException();
    }
}
