package net.sf.drftpd.remotefile;


/**
 * @author <a href="mailto:mog@linux.nu">Morgan Christiansson</a>
 */
public abstract class RemoteFileTree extends RemoteFile {
	public abstract RemoteFileTree[] listFiles();
}
