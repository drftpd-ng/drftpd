package org.drftpd.slaveselection;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.master.config.ConfigInterface;
import net.sf.drftpd.mirroring.Job;

import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id$
 */
public class DelegatingSlaveSelectionManager implements
		SlaveSelectionManagerInterface {

	private URLClassLoader _cl;

	private SlaveSelectionManagerInterface _delegate;

	public DelegatingSlaveSelectionManager(GlobalContext gctx) {
		try {
			_cl = new URLClassLoader(new URL[] { new File(
					"classes-slaveselection").toURL() });
			_delegate = (SlaveSelectionManagerInterface) _cl.loadClass(
			"se.mog.javaslaveselection.JavaSlaveSelectionManager")
			.getConstructor(new Class[] { GlobalContext.class })
			.newInstance(new Object[] { gctx });

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		// _gctx = gctx;
		// _maxUploadsPerSlaveJob = new MaxUploadsPerSlaveJob(gctx);
		// reload();
	}

	public void reload() throws IOException {
_delegate.reload();
	}

	public RemoteSlave getASlave(Collection<RemoteSlave> arg0, char arg1,
			BaseFtpConnection arg2, LinkedRemoteFileInterface arg3)
			throws NoAvailableSlaveException {
		return _delegate.getASlave(arg0, arg1, arg2, arg3);
	}

	public GlobalContext getGlobalContext() {
		//this is problably never called
		return _delegate.getGlobalContext();
	}

	public RemoteSlave getASlaveForJobDownload(Job job)
			throws NoAvailableSlaveException {
		return _delegate.getASlaveForJobDownload(job);
	}

	public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		return getASlaveForJobUpload(job, sourceSlave);
	}

}
