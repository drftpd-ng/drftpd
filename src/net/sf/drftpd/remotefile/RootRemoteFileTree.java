/*
 * Created on 2003-aug-29
 *
 * To change the template for this generated file go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
package net.sf.drftpd.remotefile;

import java.io.IOException;

import net.sf.drftpd.master.config.FtpConfig;

/**
 * @author mog
 *
 * To change the template for this generated type comment go to
 * Window>Preferences>Java>Code Generation>Code and Comments
 */
public class RootRemoteFileTree extends LinkedRemoteFile {

	/**
	 * @param cm
	 */
	public RootRemoteFileTree(FtpConfig cm) {
		super(cm);
		// TODO Auto-generated constructor stub
	}

	/**
	 * @param file
	 * @param cm
	 * @throws IOException
	 */
	public RootRemoteFileTree(RemoteFile file, FtpConfig cm)
		throws IOException {
		super(file, cm);
		// TODO Auto-generated constructor stub
	}
}
