package org.drftpd.slaveselection;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Collection;

import net.sf.drftpd.NoAvailableSlaveException;
import net.sf.drftpd.master.BaseFtpConnection;
import net.sf.drftpd.mirroring.Job;

import org.apache.log4j.Logger;
import org.drftpd.GlobalContext;
import org.drftpd.master.RemoteSlave;
import org.drftpd.remotefile.LinkedRemoteFileInterface;

/**
 * @author mog
 * @version $Id$
 */
public class DelegatingSlaveSelectionManager implements
		SlaveSelectionManagerInterface {

	private static final String CLASSNAME = "se.mog.javaslaveselection.JavaSlaveSelectionManager";

	private static final Logger logger = Logger.getLogger(DelegatingSlaveSelectionManager.class); 

	private URLClassLoader _cl;

	private SlaveSelectionManagerInterface _delegate;

	private Object _gctx;
	public DelegatingSlaveSelectionManager(GlobalContext gctx) {
		_gctx = gctx;
		init2();
	}

	public RemoteSlave getASlave(Collection<RemoteSlave> arg0, char arg1,
			BaseFtpConnection arg2, LinkedRemoteFileInterface arg3)
			throws NoAvailableSlaveException {
		return _delegate.getASlave(arg0, arg1, arg2, arg3);
	}

	public RemoteSlave getASlaveForJobDownload(Job job)
			throws NoAvailableSlaveException {
		return _delegate.getASlaveForJobDownload(job);
	}

	public RemoteSlave getASlaveForJobUpload(Job job, RemoteSlave sourceSlave)
			throws NoAvailableSlaveException {
		return getASlaveForJobUpload(job, sourceSlave);
	}

	public GlobalContext getGlobalContext() {
		//this is problably never called
		return _delegate.getGlobalContext();
	}

	public void reload() throws IOException {
		init2();
	}

	private void init2() {
		try {
			Class.forName(CLASSNAME);
			logger.warn("Was able to load slaveselection class with current classloader!");
		} catch(ClassNotFoundException e) {}
		try {
			_cl = new URLClassLoader(new URL[] { new File(
					"classes-slaveselection").toURL() });
			_delegate = (SlaveSelectionManagerInterface) _cl.loadClass(
			CLASSNAME)
			.getConstructor(new Class[] { GlobalContext.class })
			.newInstance(new Object[] { _gctx });

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
