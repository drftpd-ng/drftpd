package net.sf.drftpd.remotefile;

import java.io.IOException;

import net.sf.drftpd.SFVFile;


/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public abstract class RemoteFileTree extends RemoteFile {
	
	protected SFVFile sfvFile;
	
	public SFVFile getSFVFile() throws IOException {
		return sfvFile;
	}
	
	public abstract RemoteFileTree[] listFiles();
	
}
